package org.grubhart.pucp.tesis.module_collector.github;

import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunDto;
import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunsResponse;
import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.GithubDeploymentCollector;
import org.grubhart.pucp.tesis.module_domain.GithubPullRequestCollector;
import org.grubhart.pucp.tesis.module_domain.GithubPullRequestDto;
import org.grubhart.pucp.tesis.module_domain.GithubUserAuthenticator;
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
import java.util.stream.Stream;

/**
 * Implementación concreta que utiliza WebClient para interactuar
 * con la API de GitHub, implementando los contratos definidos en el dominio.
 */
@Component
public class GithubClientImpl implements GithubUserAuthenticator, GithubCommitCollector, GithubPullRequestCollector, GithubDeploymentCollector {

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
        String formattedSince = since.format(DateTimeFormatter.ISO_DATE_TIME);
        String initialUrl = UriComponentsBuilder.fromPath("/repos/{owner}/{repo}/commits")
                .queryParam("since", formattedSince)
                .queryParam("per_page", 100)
                .buildAndExpand(owner, repo)
                .toString();

        logger.info("Iniciando recolección paginada de commits para {}/{} desde {}", owner, repo, formattedSince);

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
                    logger.error("Error fetching commits from {}: {} - {}", currentUrl, e.getStatusCode(), e.getResponseBodyAsString(), e);
                    if (e.getStatusCode().is5xxServerError()) {
                        throw new RuntimeException("Failed to fetch commits from GitHub due to a server error: " + e.getMessage(), e);
                    }
                    break;
                }
            }
        } catch (RuntimeException e) {
            if (allCommits.isEmpty()) {
                throw e; // Rethrow if error happened on the first page
            }
            logger.warn("Error during paginated commit collection. Returning partial results. Error: {}", e.getMessage());
        }

        logger.info("Recolección paginada finalizada. Total de commits obtenidos: {}", allCommits.size());
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
                        allPullRequests.add(pr);
                    }

                } catch (WebClientResponseException e) {
                    logger.error("Error fetching pull requests from {}: {} - {}", currentUrl, e.getStatusCode(), e.getResponseBodyAsString(), e);
                    if (e.getStatusCode().is5xxServerError()) {
                        throw new RuntimeException("Failed to fetch pull requests from GitHub due to a server error: " + e.getMessage(), e);
                    }
                    break;
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
                    logger.error("Error fetching workflow runs from {}: {} - {}", currentUrl, e.getStatusCode(), e.getResponseBodyAsString(), e);
                    if (e.getStatusCode().is5xxServerError()) {
                        throw new RuntimeException("Failed to fetch workflow runs from GitHub due to a server error: " + e.getMessage(), e);
                    }
                    break;
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

    String parseNextPageUrl(List<String> linkHeaders) {
        if (linkHeaders == null) {
            return null;
        }

        Pattern nextLinkPattern = Pattern.compile("<([^>]+)>\s*;\s*rel=\"next\"");

        return linkHeaders.stream()
                .filter(Objects::nonNull)
                .flatMap(header -> Stream.of(header.split(",")))
                .map(part -> nextLinkPattern.matcher(part.trim()))
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElse(null);
    }
}
