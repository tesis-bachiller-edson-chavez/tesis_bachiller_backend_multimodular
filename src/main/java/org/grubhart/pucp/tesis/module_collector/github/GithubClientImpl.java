package org.grubhart.pucp.tesis.module_collector.github;

import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.GithubUserAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementación concreta que utiliza WebClient para interactuar
 * con la API de GitHub, implementando los contratos definidos en el dominio.
 */
@Component
public class GithubClientImpl implements GithubUserAuthenticator, GithubCommitCollector {

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
                .map(response -> response.getStatusCode() == HttpStatus.NO_CONTENT)
                .onErrorResume(WebClientResponseException.NotFound.class, e -> Mono.just(false)) // 404 significa que no es miembro
                .doOnError(e -> logger.error("Error al verificar la membresía del usuario '{}' en la organización '{}'", username, organizationName, e))
                .onErrorReturn(false) // Cualquier otro error se trata como si no fuera miembro
                .block(); // .block() es aceptable aquí para una operación síncrona de una sola vez.
    }

    @Override
    public List<GithubCommitDto> getCommits(String owner, String repo, LocalDateTime since) {
        // 1. Construir la URL inicial
        String formattedSince = since.format(DateTimeFormatter.ISO_DATE_TIME);
        String initialUrl = UriComponentsBuilder.fromPath("/repos/{owner}/{repo}/commits")
                .queryParam("since", formattedSince)
                .queryParam("per_page", 100) // Pedimos el máximo por página para ser eficientes
                .buildAndExpand(owner, repo)
                .toString();

        logger.info("Iniciando recolección paginada de commits para {}/{} desde {}", owner, repo, formattedSince);

        // 2. Preparar el bucle de paginación
        List<GithubCommitDto> allCommits = new ArrayList<>();
        String nextPageUrl = initialUrl;

        while (nextPageUrl != null) {
            logger.debug("Obteniendo página de commits: {}", nextPageUrl);

            ResponseEntity<List<GithubCommitDto>> response = webClient.get()
                    .uri(nextPageUrl)
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<List<GithubCommitDto>>() {})
                    .doOnError(e -> logger.error("Error al obtener una página de commits para {}/{}", owner, repo, e))
                    .onErrorReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of()))
                    .block();

            if (response != null && response.getBody() != null) {
                allCommits.addAll(response.getBody());
                // 3. Extraer la URL de la siguiente página de la cabecera 'Link'
                nextPageUrl = parseNextPageUrl(response.getHeaders().get("Link"));
            } else {
                nextPageUrl = null;
            }
        }

        logger.info("Recolección paginada finalizada. Total de commits obtenidos: {}", allCommits.size());
        return allCommits;
    }

    /**
     * Parsea la cabecera 'Link' de la respuesta de la API de GitHub para encontrar la URL de la siguiente página.
     * @param linkHeader La lista de valores de la cabecera 'Link'.
     * @return La URL de la siguiente página, o null si no se encuentra.
     */
    String parseNextPageUrl(List<String> linkHeader) { // Visibilidad cambiada a paquete (default)
        if (linkHeader == null || linkHeader.isEmpty()) {
            return null;
        }
        Matcher matcher = NEXT_LINK_PATTERN.matcher(linkHeader.get(0));
        return matcher.find() ? matcher.group(1) : null;
    }

}
