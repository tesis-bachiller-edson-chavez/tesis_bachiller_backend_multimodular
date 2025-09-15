package org.grubhart.pucp.tesis.module_collector.github;

import org.grubhart.pucp.tesis.module_domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementación concreta que utiliza WebClient para interactuar
 * con la API de GitHub, implementando los contratos definidos en el dominio.
 */
@Component
public class GithubClientImpl implements GithubUserAuthenticator, GithubCommitCollector, GithubPullRequestCollector {

    private static final Logger logger = LoggerFactory.getLogger(GithubClientImpl.class);
    private final WebClient webClient;

    // Expresión regular para encontrar la URL 'next' en la cabecera 'Link'
    private static final Pattern NEXT_LINK_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=\"next\"");

    public GithubClientImpl(WebClient.Builder webClientBuilder,
                            @Value("${dora.github.api-url:https://api.github.com}") String githubApiUrl,
                            @Value("${dora.github.api-token}") String githubApiToken){
        this.webClient = webClientBuilder
                .baseUrl(githubApiUrl)
                .defaultHeader("Authorization", "token " + githubApiToken)
                .build();
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

        while (nextPageUrl != null) {
            logger.debug("Obteniendo página de commits: {}", nextPageUrl);

            final String currentUrl = nextPageUrl;
            final AtomicReference<String> nextUrlFromHeader = new AtomicReference<>();

            List<GithubCommitDto> pageCommits = webClient.get()
                    .uri(currentUrl)
                    .exchangeToMono(response -> {
                        nextUrlFromHeader.set(parseNextPageUrl(response.headers().header("Link")));
                        if (response.statusCode().isError()) {
                            logger.error("Error en la respuesta de la API: {} para la URL: {}", response.statusCode(), currentUrl);
                            return Mono.just(Collections.<GithubCommitDto>emptyList());
                        }
                        return response.bodyToFlux(GithubCommitDto.class).collectList();
                    })
                    .onErrorReturn(Collections.emptyList())
                    .block();

            allCommits.addAll(pageCommits);
            nextPageUrl = nextUrlFromHeader.get();
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

        while (nextPageUrl != null) {
            logger.debug("Obteniendo página de Pull Requests: {}", nextPageUrl);

            final String currentUrl = nextPageUrl;
            final AtomicReference<String> nextUrlFromHeader = new AtomicReference<>();
            final AtomicBoolean stopPaginatingForNextIteration = new AtomicBoolean(false);

            List<GithubPullRequestDto> processedPage = webClient.get()
                    .uri(currentUrl)
                    .exchangeToMono(response -> {
                        nextUrlFromHeader.set(parseNextPageUrl(response.headers().header("Link")));
                        if (response.statusCode().isError()) {
                            logger.error("Error en la respuesta de la API: {} para la URL: {}", response.statusCode(), currentUrl);
                            stopPaginatingForNextIteration.set(true);
                            return Mono.just(Collections.<GithubPullRequestDto>emptyList());
                        }
                        return response.bodyToFlux(GithubPullRequestDto.class)
                                .takeWhile(pr -> {
                                    boolean isNewEnough = pr.getUpdatedAt() == null || !pr.getUpdatedAt().isBefore(since);
                                    if (!isNewEnough) {
                                        stopPaginatingForNextIteration.set(true);
                                    }
                                    return isNewEnough;
                                })
                                .collectList();
                    })
                    .onErrorReturn(Collections.emptyList())
                    .block();

            allPullRequests.addAll(processedPage);

            if (stopPaginatingForNextIteration.get()) {
                nextPageUrl = null;
            } else {
                nextPageUrl = nextUrlFromHeader.get();
            }
        }

        logger.info("Recolección paginada finalizada. Total de Pull Requests obtenidos: {}", allPullRequests.size());
        return allPullRequests;
    }

    String parseNextPageUrl(List<String> linkHeader) {
        if (linkHeader == null || linkHeader.isEmpty()) {
            return null;
        }
        Matcher matcher = NEXT_LINK_PATTERN.matcher(linkHeader.get(0));
        return matcher.find() ? matcher.group(1) : null;
    }
}
