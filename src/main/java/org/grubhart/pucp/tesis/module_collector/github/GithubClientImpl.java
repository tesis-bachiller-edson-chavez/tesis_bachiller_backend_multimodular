package org.grubhart.pucp.tesis.module_collector.github;

import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.GithubUserAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Implementación concreta que utiliza WebClient para interactuar
 * con la API de GitHub, implementando los contratos definidos en el dominio.
 */
@Component
public class GithubClientImpl implements GithubUserAuthenticator, GithubCommitCollector {

    private static final Logger logger = LoggerFactory.getLogger(GithubClientImpl.class);
    private final WebClient webClient;

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
                .map(response -> response.getStatusCode() == HttpStatus.NO_CONTENT)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(false)) // 404 significa que no es miembro
                .doOnError(e -> logger.error("Error al verificar la membresía del usuario '{}' en la organización '{}'", username, organizationName, e))
                .onErrorReturn(false) // Cualquier otro error se trata como si no fuera miembro
                .block(); // .block() es aceptable aquí para una operación síncrona de una sola vez.
    }

    @Override
    public List<GithubCommitDto> getCommits(String owner, String repo, LocalDateTime since) {
        String formattedSince = since.format(DateTimeFormatter.ISO_DATE_TIME);
        logger.debug("Obteniendo commits para {}/{} desde {}", owner, repo, formattedSince);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/{owner}/{repo}/commits")
                        .queryParam("since", formattedSince)
                        .build(owner, repo))
                .retrieve()
                .bodyToFlux(GithubCommitDto.class)
                .collectList()
                .doOnError(e -> logger.error("Error al obtener commits para {}/", owner, repo, e))
                .onErrorReturn(List.of()) // Devuelve una lista vacía en caso de error
                .block();
    }
}
