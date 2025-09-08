package org.grubhart.pucp.tesis.module_collector.github;


import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.GithubUserAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;


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
    private GithubCommitCollector githubCommitCollector;
    
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
    @DisplayName("Dado una respuesta 200 OK de la API (no 240), debe devolver false")
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

        // Verificamos los campos del autor del commit
        GithubCommitDto.CommitAuthor commitAuthor = firstCommit.getCommit().getAuthor();
        assertThat(commitAuthor.getName()).isEqualTo("Grubhart");
        assertThat(commitAuthor.getEmail()).isEqualTo("grubhart@example.com");
        assertThat(commitAuthor.getDate()).isNotNull();

        // Verificamos el autor a nivel superior (el usuario de GitHub que hizo el push)
        GithubCommitDto.Author topLevelAuthor = firstCommit.getAuthor();
        assertThat(topLevelAuthor).isNotNull();
        assertThat(topLevelAuthor.getLogin()).isEqualTo("Grubhart");
    }
}