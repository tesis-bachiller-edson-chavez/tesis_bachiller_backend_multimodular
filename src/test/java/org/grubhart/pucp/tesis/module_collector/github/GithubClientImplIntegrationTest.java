package org.grubhart.pucp.tesis.module_collector.github;


import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.GithubPullRequestDto;
import org.grubhart.pucp.tesis.module_domain.GithubUserAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest
@AutoConfigureWireMock(port = 0) // Inicia WireMock en un puerto aleatorio
@TestPropertySource(properties = {
        // Apuntamos la URL de la API a nuestro servidor de mocks (WireMock)
        "dora.github.api-url=http://localhost:${wiremock.server.port}",
        // Usamos un token dummy, ya que WireMock no lo validará
        "dora.github.api-token=dummy-test-token"
})
class GithubClientImplIntegrationTest {

    @Autowired
    private GithubUserAuthenticator githubUserAuthenticator;

    @Autowired
    private GithubClientImpl githubClient;

    @Autowired
    private GithubCommitCollector githubCommitCollector;

    @BeforeEach
    void setUp() {
        // Limpiamos todas las reglas de WireMock antes de cada test
        resetAllRequests();
    }

    @Test
    @DisplayName("Dado un usuario que SÍ es miembro, debe devolver true")
    void isUserMemberOfOrganization_whenUserIsMember_shouldReturnTrue() {
        // GIVEN: Simulamos que la API de GitHub responde con HTTP 204 No Content.
        stubFor(get(urlEqualTo("/orgs/test-org/members/member-user"))
                .willReturn(aResponse()
                        .withStatus(204)));

        // WHEN
        boolean isMember = githubUserAuthenticator.isUserMemberOfOrganization("member-user", "test-org");

        // THEN
        assertThat(isMember).isTrue();
    }

    @Test
    @DisplayName("Dado un usuario que NO es miembro, debe devolver false")
    void isUserMemberOfOrganization_whenUserIsNotMember_shouldReturnFalse() {
        // GIVEN: Simulamos que la API de GitHub responde con HTTP 404 Not Found.
        stubFor(get(urlEqualTo("/orgs/test-org/members/non-member-user"))
                .willReturn(aResponse()
                        .withStatus(404)));

        // WHEN
        boolean isMember = githubUserAuthenticator.isUserMemberOfOrganization("non-member-user", "test-org");

        // THEN
        assertThat(isMember).isFalse();
    }

    @Test
    @DisplayName("Dado un error 500 de la API, debe devolver false y no lanzar excepción")
    void isUserMemberOfOrganization_whenApiReturnsServerError_shouldReturnFalse() {
        // GIVEN: Simulamos un error interno del servidor.
        stubFor(get(urlEqualTo("/orgs/test-org/members/any-user"))
                .willReturn(aResponse().withStatus(500)));

        // WHEN
        boolean isMember = githubUserAuthenticator.isUserMemberOfOrganization("any-user", "test-org");

        // THEN
        assertThat(isMember).isFalse();
    }

    @Test
    @DisplayName("Dado una respuesta 200 OK de la API (no 204), debe devolver false")
    void isUserMemberOfOrganization_whenApiReturnsOk_shouldReturnFalse() {
        // GIVEN: Simulamos que la API de GitHub responde con HTTP 200 OK.
        stubFor(get(urlEqualTo("/orgs/test-org/members/any-user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        // WHEN
        boolean isMember = githubUserAuthenticator.isUserMemberOfOrganization("any-user", "test-org");

        // THEN
        assertThat(isMember).isFalse();
    }

    @Test
    @DisplayName("Debe obtener una lista de commits correctamente")
    void getCommits_shouldReturnListOfCommits() {
        String jsonResponse = """
            [
              {
                "sha": "c2a38519939553376756202026824180e8396469",
                "commit": {
                  "author": {
                    "name": "Grubhart",
                    "email": "grubhart@example.com",
                    "date": "2024-09-07T10:30:00Z"
                  },
                  "committer": {
                    "name": "GitHub",
                    "email": "noreply@github.com",
                    "date": "2024-09-07T10:30:00Z"
                  },
                  "message": "feat: initial commit"
                },
                "html_url": "https://github.com/grubhart/repo/commit/c2a38519939553376756202026824180e8396469",
                "author": {
                  "login": "Grubhart",
                  "id": 12345
                }
              }
            ]
            """;

        stubFor(get(urlMatching("/repos/owner/repo/commits\\?since=.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonResponse)));

        List<GithubCommitDto> commits = githubCommitCollector.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));

        assertThat(commits).hasSize(1);
        GithubCommitDto firstCommit = commits.get(0);
        assertThat(firstCommit.getSha()).isEqualTo("c2a38519939553376756202026824180e8396469");
        assertThat(firstCommit.getCommit().getMessage()).isEqualTo("feat: initial commit");
    }

    @Test
    @DisplayName("Cuando la API de commits devuelve 5xx, debe lanzar una RuntimeException")
    void getCommits_whenApiReturns5xxError_shouldThrowRuntimeException() {
        stubFor(get(urlMatching("/repos/owner/repo/commits\\?since=.*"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(RuntimeException.class, () -> {
            githubCommitCollector.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));
        });
    }

    @Test
    @DisplayName("Cuando la API de commits devuelve 4xx, debe devolver una lista vacía")
    void getCommits_whenApiReturns4xxError_shouldReturnEmptyList() {
        stubFor(get(urlMatching("/repos/owner/repo/commits\\?since=.*"))
                .willReturn(aResponse().withStatus(404)));

        List<GithubCommitDto> commits = githubCommitCollector.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));

        assertThat(commits).isEmpty();
    }

    @Test
    @DisplayName("Cuando una llamada paginada a la API de commits falla, debe devolver los commits de las páginas exitosas")
    void getCommits_whenPaginatedCallFails_shouldReturnPartialList() {
        String firstPageJsonResponse = """
             [
               { "sha": "c2a3851" }
             ]
             """;
        stubFor(get(urlPathEqualTo("/repos/owner/repo/commits"))
                .withQueryParam("since", matching(".*"))
                .withQueryParam("per_page", matching(".*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link", "<http://localhost:${wiremock.server.port}/repos/owner/repo/commits_page2>; rel=\"next\"")
                        .withBody(firstPageJsonResponse)));

        stubFor(get(urlEqualTo("/repos/owner/repo/commits_page2"))
                .willReturn(aResponse()
                        .withStatus(500)));

        List<GithubCommitDto> commits = githubCommitCollector.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));

        assertThat(commits).hasSize(1);
        assertThat(commits.get(0).getSha()).isEqualTo("c2a3851");
    }

    @Test
    @DisplayName("Cuando la API de pull requests devuelve 5xx, debe lanzar RuntimeException")
    void getPullRequests_whenApiReturns5xxError_shouldThrowRuntimeException() {
        stubFor(get(urlPathEqualTo("/repos/owner/repo/pulls"))
                .willReturn(aResponse().withStatus(500)));

        assertThrows(RuntimeException.class, () -> {
            githubClient.getPullRequests("owner", "repo", LocalDateTime.now());
        });
    }

    @Test
    @DisplayName("Cuando la API de pull requests devuelve 4xx, debe devolver una lista vacía")
    void getPullRequests_whenApiReturns4xxError_shouldReturnEmptyList() {
        stubFor(get(urlPathEqualTo("/repos/owner/repo/pulls"))
                .willReturn(aResponse().withStatus(404)));

        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests("owner", "repo", LocalDateTime.now());

        assertThat(pullRequests).isEmpty();
    }

    @Test
    @DisplayName("getPullRequests debe detener la paginación cuando encuentra un PR antiguo")
    void getPullRequests_whenOldPrIsFound_shouldStopPaging(@Value("${wiremock.server.port}") int wiremockPort) {
// 1. Arrange
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        String owner = "owner";
        String repo = "repo";

        // Respuesta JSON para la Página 1 (PR reciente)
        String recentPRJson = String.format("""
    [
      {
        "id": 1,
        "state": "open",
        "updated_at": "%s"
      }
    ]
    """, since.plusHours(1).format(DateTimeFormatter.ISO_DATE_TIME));

        // Respuesta JSON para la Página 2 (PR antiguo)
        String oldPRJson = String.format("""
    [
      {
        "id": 2,
        "state": "closed",
        "updated_at": "%s"
      }
    ]
    """, since.minusHours(1).format(DateTimeFormatter.ISO_DATE_TIME));

        // Construimos la URL de la siguiente página dinámicamente con el puerto correcto
        String nextPageUrl = String.format("http://localhost:%d/repos/owner/repo/pulls?page=2", wiremockPort);

        // Configuración de WireMock para la Página 1
        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/pulls"))
                .withQueryParam("state", equalTo("all"))
                .withQueryParam("sort", equalTo("updated"))
                .withQueryParam("direction", equalTo("desc"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        // Usamos la URL construida dinámicamente para la cabecera 'Link'
                        .withHeader("Link", String.format("<%s>; rel=\"next\"", nextPageUrl))
                        .withBody(recentPRJson)));

        // Configuración de WireMock para la Página 2
        stubFor(get(urlEqualTo("/repos/owner/repo/pulls?page=2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(oldPRJson)));

        // 2. Act
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests(owner, repo, since);

        // 3. Assert
        assertThat(pullRequests).isNotNull();
        // Solo el PR reciente debe estar en la lista, porque la paginación se detuvo
        assertThat(pullRequests).hasSize(1);
        assertThat(pullRequests.get(0).getId()).isEqualTo(1L);

        // --- CAMBIOS AQUÍ ---
        // Verificamos que se hizo la llamada a la primera página (con sus parámetros)
        verify(1, getRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls"))
                .withQueryParam("state", equalTo("all"))
                .withQueryParam("sort", equalTo("updated"))
                .withQueryParam("direction", equalTo("desc"))
                .withQueryParam("per_page", equalTo("100")));

        // Verificamos que también se hizo la llamada a la segunda página
        verify(1, getRequestedFor(urlEqualTo("/repos/owner/repo/pulls?page=2")));
    }
}
