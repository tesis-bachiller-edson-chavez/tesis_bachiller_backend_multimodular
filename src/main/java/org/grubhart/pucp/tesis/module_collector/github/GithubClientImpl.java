package org.grubhart.pucp.tesis.module_collector.github;

import org.grubhart.pucp.tesis.module_collector.github.dto.GithubMemberDto;
import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunDto;
import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunsResponse;
import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.GithubDeploymentCollector;
import org.grubhart.pucp.tesis.module_domain.GithubPullRequestCollector;
import org.grubhart.pucp.tesis.module_domain.GithubPullRequestDto;
import org.grubhart.pucp.tesis.module_domain.GithubRepositoryCollector;
import org.grubhart.pucp.tesis.module_domain.GithubRepositoryDto;
import org.grubhart.pucp.tesis.module_domain.GithubUserAuthenticator;
import org.grubhart.pucp.tesis.module_domain.GithubUserCollector;
import org.grubhart.pucp.tesis.module_domain.OrganizationMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementación concreta que utiliza WebClient para interactuar
 * con la API de GitHub, implementando los contratos definidos en el dominio.
 */
@Component
public class GithubClientImpl implements GithubUserAuthenticator, GithubCommitCollector, GithubPullRequestCollector, GithubDeploymentCollector, GithubUserCollector, GithubRepositoryCollector {

    private static final Logger logger = LoggerFactory.getLogger(GithubClientImpl.class);
    private final WebClient webClient;

    @Autowired
    public GithubClientImpl(WebClient.Builder webClientBuilder,
                            @Value("${dora.github.api-url:https://api.github.com}") String githubApiUrl,
                            @Value("${dora.github.api-token}") String githubApiToken){
        this.webClient = webClientBuilder
                .baseUrl(githubApiUrl)
                .defaultHeader("Authorization", "token " + githubApiToken)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    GithubClientImpl(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public boolean isUserMemberOfOrganization(String username, String organizationName) {
        logger.debug("Verificando si el usuario '{}' es miembro de la organización '{}'", username, organizationName);
        return webClient.get()
                .uri("/orgs/{org}/members/{username}", organizationName, username)
                .retrieve()
                .toBodilessEntity()
                .map(response -> response.getStatusCode() == HttpStatus.NO_CONTENT) // Lógica corregida: 204 es miembro
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(false)) // 404 significa que no es miembro
                .doOnError(e -> logger.error("Error al verificar la membresía del usuario '{}' en la organización '{}'", username, organizationName, e))
                .onErrorReturn(false) // Cualquier otro error se trata como si no fuera miembro
                .block(); // .block() es aceptable aquí para una operación síncrona de una sola vez.
    }

    @Override
    public List<GithubCommitDto> getCommits(String owner, String repo, LocalDateTime since) {
        logger.info("Iniciando recolección de commits de main para {}/{} desde {}",
                owner, repo, since.format(DateTimeFormatter.ISO_DATE_TIME));

        String formattedSince = since.format(DateTimeFormatter.ISO_DATE_TIME);
        String initialUrl = UriComponentsBuilder.fromPath("/repos/{owner}/{repo}/commits")
                .queryParam("since", formattedSince)
                .queryParam("per_page", 100)
                .buildAndExpand(owner, repo)
                .toString();

        List<GithubCommitDto> allCommits = new ArrayList<>();
        String nextPageUrl = initialUrl;

        try {
            while (nextPageUrl != null) {
                final String currentUrl = nextPageUrl;
                try {
                    ResponseEntity<List<GithubCommitDto>> responseEntity = webClient.get()
                            .uri(currentUrl)
                            .retrieve()
                            .toEntityList(GithubCommitDto.class)
                            .block();

                    if (responseEntity != null) {
                        if (responseEntity.getBody() != null) {
                            allCommits.addAll(responseEntity.getBody());
                        }
                        nextPageUrl = parseNextPageUrl(responseEntity.getHeaders().get("Link"));
                    } else {
                        nextPageUrl = null;
                    }
                } catch (WebClientResponseException e) {
                    logger.error("Error fetching commits from {}: {} {}", currentUrl,
                            e.getStatusCode().value(), e.getStatusText(), e);
                    if (e.getStatusCode().is5xxServerError()) {
                        throw new RuntimeException("Failed to fetch commits from GitHub: " + e.getMessage(), e);
                    }
                    nextPageUrl = null;
                }
            }
        } catch (RuntimeException e) {
            if (allCommits.isEmpty()) {
                throw e;
            }
            logger.warn("Error during commits collection. Returning partial results. Error: {}", e.getMessage());
        }

        logger.info("Recolección finalizada. Total de commits obtenidos: {}", allCommits.size());

        // NOTA: Retornamos TODOS los commits (incluidos merge) para mantener el grafo parent-child completo.
        // El filtrado de merge commits se hace en DeveloperDashboardService/TechLeadDashboardService
        // al calcular métricas, NO aquí.
        // La autoría real se extrae del campo dto.commit.author.email (no dto.author.login)
        // en el constructor de la entidad Commit.
        return allCommits;
    }

    @Override
    public List<GithubPullRequestDto> getPullRequests(String owner, String repo, LocalDateTime since) {
        String initialUrl = UriComponentsBuilder.fromPath("/repos/{owner}/{repo}/pulls")
                .queryParam("state", "all")
                .queryParam("sort", "updated")
                .queryParam("direction", "desc")
                .queryParam("per_page", 100)
                .buildAndExpand(owner, repo)
                .toString();

        logger.info("Iniciando recolección paginada de Pull Requests para {}/{}", owner, repo);

        List<GithubPullRequestDto> allPullRequests = new ArrayList<>();
        String nextPageUrl = initialUrl;
        boolean shouldStop = false;

        try {
            while (nextPageUrl != null && !shouldStop) {
                final String currentUrl = nextPageUrl;
                try {
                    ResponseEntity<List<GithubPullRequestDto>> responseEntity = webClient.get()
                            .uri(currentUrl)
                            .retrieve()
                            .toEntityList(GithubPullRequestDto.class)
                            .block();

                    if (responseEntity == null || responseEntity.getBody() == null) {
                        break;
                    }

                    List<GithubPullRequestDto> pagePRs = responseEntity.getBody();
                    nextPageUrl = parseNextPageUrl(responseEntity.getHeaders().get("Link"));

                    for (GithubPullRequestDto pr : pagePRs) {
                        if (pr.getUpdatedAt() != null && pr.getUpdatedAt().isBefore(since)) {
                            shouldStop = true;
                            continue;
                        }
                        // Enrich DTO with the first commit SHA
                        try {
                            String firstCommitSha = getFirstCommitShaForPr(owner, repo, pr.getNumber());
                            pr.setFirstCommitSha(firstCommitSha);
                        } catch (Exception e) {
                            logger.error("Failed to fetch first commit for PR #{} in {}/{}. Error: {}", pr.getNumber(), owner, repo, e.getMessage());
                        }
                        allPullRequests.add(pr);
                    }

                } catch (WebClientResponseException e) {
                    logger.error("Error fetching pull requests from {}: {} {}", currentUrl, e.getStatusCode().value(), e.getStatusText(), e);
                    if (e.getStatusCode().is5xxServerError()) {
                        throw new RuntimeException("Failed to fetch pull requests from GitHub due to a server error: " + e.getMessage(), e);
                    }
                    nextPageUrl = null; // Stop pagination on client or non-5xx server errors
                }
            }
        } catch (RuntimeException e) {
            if (allPullRequests.isEmpty()) {
                throw e;
            }
            logger.warn("Error during paginated pull request collection. Returning partial results. Error: {}", e.getMessage());
        }

        logger.info("Recolección paginada finalizada. Total de Pull Requests obtenidos: {}", allPullRequests.size());
        return allPullRequests;
    }

    protected Mono<ResponseEntity<List<GithubCommitDto>>> createCommitsRequestMono(String owner, String repo, int prNumber) {
        String url = UriComponentsBuilder.fromPath("/repos/{owner}/{repo}/pulls/{prNumber}/commits")
                .queryParam("per_page", 1)
                .buildAndExpand(owner, repo, prNumber)
                .toString();
        return webClient.get()
                .uri(url)
                .retrieve()
                .toEntityList(GithubCommitDto.class);
    }

    protected List<GithubCommitDto> fetchCommitsForPr(String owner, String repo, int prNumber) {
        try {
            ResponseEntity<List<GithubCommitDto>> responseEntity = createCommitsRequestMono(owner, repo, prNumber).block();

            if (responseEntity != null) {
                return responseEntity.getBody();
            }
            return null;
        } catch (WebClientResponseException e) {
            logger.error("Failed to fetch commits for PR #{} in {}/{}: {} {}", prNumber, owner, repo, e.getStatusCode().value(), e.getStatusText(), e);
            return null;
        }
    }

    public String getFirstCommitShaForPr(String owner, String repo, int prNumber) {
        logger.debug("Fetching first commit for PR #{} in {}/{}", prNumber, owner, repo);

        List<GithubCommitDto> commits = fetchCommitsForPr(owner, repo, prNumber);

        if (commits != null && !commits.isEmpty()) {
            String sha = commits.get(0).getSha();
            logger.info("First commit for PR #{} is {}", prNumber, sha);
            return sha;
        } else {
            logger.warn("No commits found for PR #{}", prNumber);
            throw new RuntimeException("No commits found for PR #" + prNumber);
        }
    }

    @Override
    public List<GitHubWorkflowRunDto> getWorkflowRuns(String owner, String repo, String workflowFileName, LocalDateTime since) {
        String initialUrl = UriComponentsBuilder.fromPath("/repos/{owner}/{repo}/actions/workflows/{workflowFileName}/runs")
                .queryParam("per_page", 100)
                .buildAndExpand(owner, repo, workflowFileName)
                .toString();

        if (since == null) {
            logger.info("Iniciando recolección paginada de Workflow Runs para {}/{} y workflow '{}' (primera sincronización)", owner, repo, workflowFileName);
        } else {
            logger.info("Iniciando recolección paginada de Workflow Runs para {}/{} y workflow '{}' desde {}", owner, repo, workflowFileName, since);
        }

        List<GitHubWorkflowRunDto> allWorkflowRuns = new ArrayList<>();
        String nextPageUrl = initialUrl;
        boolean shouldStop = false;

        try {
            while (nextPageUrl != null && !shouldStop) {
                final String currentUrl = nextPageUrl;
                try {
                    ResponseEntity<GitHubWorkflowRunsResponse> responseEntity = webClient.get()
                            .uri(currentUrl)
                            .retrieve()
                            .toEntity(GitHubWorkflowRunsResponse.class)
                            .block();

                    if (responseEntity == null || !responseEntity.hasBody() || responseEntity.getBody().getWorkflowRuns() == null) {
                        break;
                    }

                    nextPageUrl = parseNextPageUrl(responseEntity.getHeaders().get("Link"));

                    for (GitHubWorkflowRunDto run : responseEntity.getBody().getWorkflowRuns()) {
                        if (since != null && run.getCreatedAt() != null && run.getCreatedAt().isBefore(since)) {
                            shouldStop = true;
                            continue;
                        }
                        allWorkflowRuns.add(run);
                    }
                } catch (WebClientResponseException e) {
                    logger.error("Error fetching workflow runs from {}: {} {}", currentUrl, e.getStatusCode().value(), e.getStatusText(), e);
                    if (e.getStatusCode().is5xxServerError()) {
                        throw new RuntimeException("Failed to fetch workflow runs from GitHub due to a server error: " + e.getMessage(), e);
                    }
                    nextPageUrl = null; // Stop pagination on client or non-5xx server errors
                }
            }
        } catch (RuntimeException e) {
            if (allWorkflowRuns.isEmpty()) {
                throw e;
            }
            logger.warn("Error during paginated workflow run collection. Returning partial results. Error: {}", e.getMessage());
        }

        logger.info("Recolección paginada finalizada. Total de Workflow Runs obtenidos: {}", allWorkflowRuns.size());
        return allWorkflowRuns;
    }

    @Override
    public List<OrganizationMember> getOrganizationMembers(String organizationName) {
        String initialUrl = UriComponentsBuilder.fromPath("/orgs/{org}/members")
                .queryParam("per_page", 100)
                .buildAndExpand(organizationName)
                .toString();

        logger.info("Iniciando recolección paginada de miembros para la organización '{}'", organizationName);

        List<GithubMemberDto> allMembers = new ArrayList<>();
        String nextPageUrl = initialUrl;

        try {
            while (nextPageUrl != null) {
                final String currentUrl = nextPageUrl;
                try {
                    ResponseEntity<List<GithubMemberDto>> responseEntity = webClient.get()
                            .uri(currentUrl)
                            .retrieve()
                            .toEntityList(GithubMemberDto.class)
                            .block();

                    if (responseEntity != null) {
                        if (responseEntity.getBody() != null) {
                            allMembers.addAll(responseEntity.getBody());
                        }
                        nextPageUrl = parseNextPageUrl(responseEntity.getHeaders().get("Link"));
                    } else {
                        nextPageUrl = null;
                    }
                } catch (WebClientResponseException e) {
                    logger.error("Error fetching members from {}: {} {}", currentUrl, e.getStatusCode().value(), e.getStatusText(), e);
                    if (e.getStatusCode().is5xxServerError()) {
                        throw new RuntimeException("Failed to fetch members from GitHub due to a server error: " + e.getMessage(), e);
                    }
                    nextPageUrl = null; // Stop pagination on client or non-5xx server errors
                }
            }
        } catch (RuntimeException e) {
            if (allMembers.isEmpty()) {
                throw e; // Rethrow if error happened on the first page
            }
            logger.warn("Error during paginated member collection. Returning partial results. Error: {}", e.getMessage());
        }

        logger.info("Recolección paginada finalizada. Total de miembros obtenidos: {}", allMembers.size());
        return allMembers.stream()
                .map(dto -> new OrganizationMember(dto.id(), dto.login(), dto.avatarUrl()))
                .collect(Collectors.toList());
    }

    @Override
    public List<GithubRepositoryDto> getOrgRepositories(String organizationName) {
        String initialUrl = UriComponentsBuilder.fromPath("/orgs/{org}/repos")
                .queryParam("type", "all")
                .queryParam("sort", "updated")
                .queryParam("per_page", 70)
                .buildAndExpand(organizationName)
                .toString();

        logger.info("Fetching repositories for organization '{}' from GitHub", organizationName);

        List<GithubRepositoryDto> allRepositories = new ArrayList<>();
        String nextPageUrl = initialUrl;

        try {
            while (nextPageUrl != null) {
                final String currentUrl = nextPageUrl;
                logger.info("Requesting repositories from URL: {}", currentUrl);
                try {
                    ResponseEntity<List<GithubRepositoryDto>> responseEntity = webClient.get()
                            .uri(currentUrl)
                            .retrieve()
                            .toEntityList(GithubRepositoryDto.class)
                            .block();

                    if (responseEntity != null) {
                        if (responseEntity.getBody() != null) {
                            allRepositories.addAll(responseEntity.getBody());
                        }
                        nextPageUrl = parseNextPageUrl(responseEntity.getHeaders().get("Link"));
                    } else {
                        nextPageUrl = null;
                    }
                } catch (WebClientResponseException e) {
                    logger.error("Error fetching repositories from {}: {} {}", currentUrl, e.getStatusCode().value(), e.getStatusText(), e);
                    if (e.getStatusCode().is5xxServerError()) {
                        throw new RuntimeException("Failed to fetch repositories from GitHub due to a server error: " + e.getMessage(), e);
                    }
                    nextPageUrl = null; // Stop pagination on client or non-5xx server errors
                }
            }
        } catch (RuntimeException e) {
            if (allRepositories.isEmpty()) {
                throw e; // Rethrow if error happened on the first page
            }
            logger.warn("Error during paginated repository collection. Returning partial results. Error: {}", e.getMessage());
        }

        logger.info("Successfully fetched {} repositories from GitHub for organization '{}'", allRepositories.size(), organizationName);
        return allRepositories;
    }

    String parseNextPageUrl(List<String> linkHeaders) {
        if (linkHeaders == null || linkHeaders.isEmpty()) {
            return null;
        }

        Pattern nextLinkPattern = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"next\"");

        for (String linkHeader : linkHeaders) {
            if (linkHeader == null) {
                continue;
            }
            // The header can contain multiple links, separated by a comma
            String[] links = linkHeader.split(",\\s*");
            for (String link : links) {
                Matcher matcher = nextLinkPattern.matcher(link);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }

        return null;
    }
}
