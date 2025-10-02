package org.grubhart.pucp.tesis.module_collector.github;

import org.grubhart.pucp.tesis.module_domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
        "dora.github.api-url=http://localhost:${wiremock.server.port}",
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
        resetAllRequests();
    }

    @Test
    @DisplayName("Dado un usuario que SÍ es miembro, debe devolver true")
    void isUserMemberOfOrganization_whenUserIsMember_shouldReturnTrue() {
        stubFor(get(urlEqualTo("/orgs/test-org/members/member-user"))
                .willReturn(aResponse()
                        .withStatus(204)));

        boolean isMember = githubUserAuthenticator.isUserMemberOfOrganization("member-user", "test-org");

        assertThat(isMember).isTrue();
    }

    @Test
    @DisplayName("Dado un usuario que NO es miembro, debe devolver false")
    void isUserMemberOfOrganization_whenUserIsNotMember_shouldReturnFalse() {
        stubFor(get(urlEqualTo("/orgs/test-org/members/non-member-user"))
                .willReturn(aResponse()
                        .withStatus(404)));

        boolean isMember = githubUserAuthenticator.isUserMemberOfOrganization("non-member-user", "test-org");

        assertThat(isMember).isFalse();
    }

    @Test
    @DisplayName("Dado un error 500 de la API, debe devolver false y no lanzar excepción")
    void isUserMemberOfOrganization_whenApiReturnsServerError_shouldReturnFalse() {
        stubFor(get(urlEqualTo("/orgs/test-org/members/any-user"))
                .willReturn(aResponse().withStatus(500)));

        boolean isMember = githubUserAuthenticator.isUserMemberOfOrganization("any-user", "test-org");

        assertThat(isMember).isFalse();
    }

    @Test
    @DisplayName("Dado una respuesta 200 OK de la API (no 204), debe devolver false")
    void isUserMemberOfOrganization_whenApiReturnsOk_shouldReturnFalse() {
        stubFor(get(urlEqualTo("/orgs/test-org/members/any-user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        boolean isMember = githubUserAuthenticator.isUserMemberOfOrganization("any-user", "test-org");

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

    @Test
    @DisplayName("getPullRequests debe detenerse y no llamar a la siguiente página si encuentra un PR antiguo en la primera")
    void getPullRequests_shouldNotFetchNextPage_whenOldPrIsFoundOnFirstPage(@Value("${wiremock.server.port}") int wiremockPort) {
        // 1. Arrange
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        String owner = "owner";
        String repo = "repo";

        // Creamos un JSON para la primera página que contiene un PR reciente y uno antiguo.
        // El cliente debe procesar el reciente, encontrar el antiguo, y detenerse.
        String firstPageJson = String.format("""
        [
          {
            "id": 1,
            "state": "open",
            "updated_at": "%s"
          },
          {
            "id": 2,
            "state": "closed",
            "updated_at": "%s"
          }
        ]
        """, since.plusHours(1).format(DateTimeFormatter.ISO_DATE_TIME), // PR Reciente
                since.minusHours(1).format(DateTimeFormatter.ISO_DATE_TIME) // PR Antiguo
        );

        // Construimos la URL de la siguiente página, aunque esperamos que NUNCA se llame.
        String nextPageUrl = String.format("http://localhost:%d/repos/owner/repo/pulls?page=2", wiremockPort);

        // Configuración de WireMock para la Página 1
        // Esta página contiene un PR antiguo y un link a la siguiente página.
        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/pulls"))
                .withQueryParam("state", equalTo("all"))
                .withQueryParam("sort", equalTo("updated"))
                .withQueryParam("direction", equalTo("desc"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link", String.format("<%s>; rel=\"next\"", nextPageUrl))
                        .withBody(firstPageJson)));

        // 2. Act
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests(owner, repo, since);

        // 3. Assert
        assertThat(pullRequests).isNotNull();
        // Solo el PR reciente debe estar en la lista.
        assertThat(pullRequests).hasSize(1);
        assertThat(pullRequests.get(0).getId()).isEqualTo(1L);

        // Verificamos que se hizo la llamada a la primera página.
        verify(1, getRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls")));

        // La aserción CLAVE: Verificamos que NUNCA se hizo la llamada a la segunda página.
        verify(0, getRequestedFor(urlEqualTo("/repos/owner/repo/pulls?page=2")));
    }

    @Test
    @DisplayName("getPullRequests debe devolver resultados parciales si una llamada paginada falla")
    void getPullRequests_shouldReturnPartialResults_whenPaginatedCallFails(@Value("${wiremock.server.port}") int wiremockPort) {
        // 1. Arrange
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        String owner = "owner";
        String repo = "repo";

        // Respuesta JSON para la Página 1 (exitosa)
        String firstPageJson = String.format("""
        [
          {
            "id": 1,
            "state": "open",
            "updated_at": "%s"
          }
        ]
        """, since.plusHours(1).format(DateTimeFormatter.ISO_DATE_TIME));

        // Construimos la URL de la siguiente página, que sabemos que va a fallar.
        String nextPageUrl = String.format("http://localhost:%d/repos/owner/repo/pulls?page=2", wiremockPort);

        // Configuración de WireMock para la Página 1 (éxito)
        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/pulls"))
                .withQueryParam("state", equalTo("all"))
                // Añadimos los otros query params para que el stub sea más específico
                .withQueryParam("sort", equalTo("updated"))
                .withQueryParam("direction", equalTo("desc"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link", String.format("<%s>; rel=\"next\"", nextPageUrl))
                        .withBody(firstPageJson)));

        // Configuración de WireMock para la Página 2 (fallo)
        stubFor(get(urlEqualTo("/repos/owner/repo/pulls?page=2"))
                .willReturn(aResponse()
                        .withStatus(500) // Error interno del servidor
                        .withBody("{\"message\":\"Internal Server Error\"}")));

        // 2. Act
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests(owner, repo, since);

        // 3. Assert
        // Verificamos que no se lanzó una excepción y que recibimos los resultados de la primera página.
        assertThat(pullRequests).isNotNull();
        assertThat(pullRequests).hasSize(1);
        assertThat(pullRequests.get(0).getId()).isEqualTo(1L);

        // --- CAMBIO CLAVE AQUÍ ---
        // Verificamos que se intentaron ambas llamadas, pero de forma específica.
        // Verificación para la primera página, con todos sus parámetros.
        verify(1, getRequestedFor(urlPathEqualTo("/repos/owner/repo/pulls"))
                .withQueryParam("state", equalTo("all"))
                .withQueryParam("sort", equalTo("updated"))
                .withQueryParam("direction", equalTo("desc"))
                .withQueryParam("per_page", equalTo("100")));

        // Verificación para la segunda página.
        verify(1, getRequestedFor(urlEqualTo("/repos/owner/repo/pulls?page=2")));
    }

    @Test
    @DisplayName("getWorkflowRuns debe obtener todas las páginas cuando 'since' es nulo (primera sincronización)")
    void getWorkflowRuns_whenSinceIsNull_shouldFetchAllPages(@Value("${wiremock.server.port}") int wiremockPort) {
        // 1. Arrange
        String owner = "owner";
        String repo = "repo";
        String workflowFile = "workflow.yml";

        // Respuesta JSON para la Página 1
        String firstPageJson = """
        {
          "total_count": 2,
          "workflow_runs": [
            { "id": 101, "status": "completed", "conclusion": "success", "created_at": "2025-09-28T10:00:00Z" }
          ]
        }
        """;

        // Respuesta JSON para la Página 2
        String secondPageJson = """
        {
          "total_count": 2,
          "workflow_runs": [
            { "id": 100, "status": "completed", "conclusion": "success", "created_at": "2025-09-27T10:00:00Z" }
          ]
        }
        """;

        // Construimos la URL de la siguiente página
        String nextPageUrl = String.format("http://localhost:%d/repos/%s/%s/actions/workflows/%s/runs?page=2",
                wiremockPort, owner, repo, workflowFile);

        // Configuración de WireMock para la Página 1
        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link", String.format("<%s>; rel=\"next\"", nextPageUrl))
                        .withBody(firstPageJson)));

        // Configuración de WireMock para la Página 2
        stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs?page=2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(secondPageJson)));

        // 2. Act
        // Llamamos al método con 'since' como null
        List<GitHubWorkflowRunDto> workflowRuns = githubClient.getWorkflowRuns(owner, repo, workflowFile, null);

        // 3. Assert
        assertThat(workflowRuns).isNotNull();
        // Verificamos que se recolectaron los runs de AMBAS páginas
        assertThat(workflowRuns).hasSize(2);
        assertThat(workflowRuns).extracting(GitHubWorkflowRunDto::getId).containsExactlyInAnyOrder(101L, 100L);

        // Verificamos que se hicieron ambas llamadas a la API
        verify(1, getRequestedFor(urlPathEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs"))
                .withQueryParam("per_page", equalTo("100")));

        verify(1, getRequestedFor(urlEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs?page=2")));
    }

    @Test
    @DisplayName("getWorkflowRuns debe detener la paginación si encuentra un run antiguo")
    void getWorkflowRuns_shouldStopPaging_whenOldRunIsFound(@Value("${wiremock.server.port}") int wiremockPort) {
        // 1. Arrange
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        String owner = "owner";
        String repo = "repo";
        String workflowFile = "workflow.yml";

        // Respuesta JSON para la Página 1: contiene un run reciente y uno antiguo.
        String firstPageJson = String.format("""
        {
          "total_count": 2,
          "workflow_runs": [
            { "id": 101, "status": "completed", "conclusion": "success", "created_at": "%s" },
            { "id": 99, "status": "completed", "conclusion": "failure", "created_at": "%s" }
          ]
        }
        """,
                // --- CAMBIO AQUÍ ---
                // Convertimos a ZonedDateTime en UTC y luego formateamos con ISO_INSTANT
                since.plusHours(1).atZone(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT), // Reciente
                since.minusHours(1).atZone(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)  // Antiguo
        );

        // Construimos la URL de la siguiente página, que esperamos que NUNCA se llame.
        String nextPageUrl = String.format("http://localhost:%d/repos/%s/%s/actions/workflows/%s/runs?page=2",
                wiremockPort, owner, repo, workflowFile);

        // Configuración de WireMock para la Página 1
        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        // Incluimos el link a la siguiente página para probar que el cliente lo ignora.
                        .withHeader("Link", String.format("<%s>; rel=\"next\"", nextPageUrl))
                        .withBody(firstPageJson)));

        // 2. Act
        // Llamamos al método con un 'since' válido.
        List<GitHubWorkflowRunDto> workflowRuns = githubClient.getWorkflowRuns(owner, repo, workflowFile, since);

        // 3. Assert
        assertThat(workflowRuns).isNotNull();
        // Solo el run reciente debe estar en la lista, porque el antiguo detuvo la recolección.
        assertThat(workflowRuns).hasSize(1);
        assertThat(workflowRuns.get(0).getId()).isEqualTo(101L);

        // Verificamos que se hizo la llamada a la primera página.
        verify(1, getRequestedFor(urlPathEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs")));

        // La aserción CLAVE: Verificamos que NUNCA se hizo la llamada a la segunda página.
        verify(0, getRequestedFor(urlEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs?page=2")));
    }

    @Test
    @DisplayName("getWorkflowRuns debe devolver resultados parciales si una llamada paginada falla")
    void getWorkflowRuns_shouldReturnPartialResults_whenPaginatedCallFails(@Value("${wiremock.server.port}") int wiremockPort) {
        // 1. Arrange
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        String owner = "owner";
        String repo = "repo";
        String workflowFile = "workflow.yml";

        // --- CAMBIO AQUÍ ---
        // Se formatea la fecha dinámicamente para asegurar que sea más reciente que 'since'.
        String recentDate = since.plusHours(1).atZone(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);

        // Respuesta JSON para la Página 1 (exitosa)
        String firstPageJson = String.format("""
        {
          "total_count": 2,
          "workflow_runs": [
            { "id": 101, "status": "completed", "conclusion": "success", "created_at": "%s" }
          ]
        }
        """, recentDate);

        // Construimos la URL de la siguiente página, que sabemos que va a fallar.
        String nextPageUrl = String.format("http://localhost:%d/repos/%s/%s/actions/workflows/%s/runs?page=2",
                wiremockPort, owner, repo, workflowFile);

        // Configuración de WireMock para la Página 1 (éxito)
        stubFor(get(urlPathEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs"))
                .withQueryParam("per_page", equalTo("100"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("Link", String.format("<%s>; rel=\"next\"", nextPageUrl))
                        .withBody(firstPageJson)));

        // Configuración de WireMock para la Página 2 (fallo)
        stubFor(get(urlEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs?page=2"))
                .willReturn(aResponse()
                        .withStatus(500) // Error interno del servidor
                        .withBody("{\"message\":\"Internal Server Error\"}")));

        // 2. Act
        List<GitHubWorkflowRunDto> workflowRuns = githubClient.getWorkflowRuns(owner, repo, workflowFile, since);

        // 3. Assert
        // Verificamos que no se lanzó una excepción y que recibimos los resultados de la primera página.
        assertThat(workflowRuns).isNotNull();
        assertThat(workflowRuns).hasSize(1);
        assertThat(workflowRuns.get(0).getId()).isEqualTo(101L);

        // Verificamos que se intentaron ambas llamadas de forma específica.
        verify(1, getRequestedFor(urlPathEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs"))
                .withQueryParam("per_page", equalTo("100")));

        verify(1, getRequestedFor(urlEqualTo("/repos/" + owner + "/" + repo + "/actions/workflows/" + workflowFile + "/runs?page=2")));
    }
}
