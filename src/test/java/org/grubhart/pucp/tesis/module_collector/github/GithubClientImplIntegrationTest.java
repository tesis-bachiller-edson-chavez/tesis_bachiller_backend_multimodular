package org.grubhart.pucp.tesis.module_collector.github;

import org.grubhart.pucp.tesis.module_administration.GithubClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        // Especificamos las clases que necesitamos para este test de integración,
        // haciendo el contexto de Spring más ligero.
        GithubClientImpl.class,
        org.grubhart.pucp.tesis.module_collector.config.WebClientConfig.class
})
@AutoConfigureWireMock(port = 0) // Inicia WireMock en un puerto aleatorio
@TestPropertySource(properties = {
        // Apuntamos la URL de la API a nuestro servidor de mocks (WireMock)
        "dora.github.api-url=http://localhost:${wiremock.server.port}",
        // Usamos un token dummy, ya que WireMock no lo validará
        "dora.github.api-token=dummy-test-token"
})
class GithubClientImplIntegrationTest {

    @Autowired
    private GithubClient githubClient;

    @Test
    @DisplayName("Dado un usuario que SÍ es miembro, debe devolver true")
    void isUserMemberOfOrganization_whenUserIsMember_shouldReturnTrue() {
        // GIVEN: Simulamos que la API de GitHub responde con HTTP 204 No Content.
        stubFor(get(urlEqualTo("/orgs/test-org/members/member-user"))
                .willReturn(aResponse()
                        .withStatus(204)));

        // WHEN: Llamamos a nuestro cliente
        boolean isMember = githubClient.isUserMemberOfOrganization("member-user", "test-org");

        // THEN: Verificamos que el resultado es el esperado
        assertThat(isMember).isTrue();
    }

    @Test
    @DisplayName("Dado un usuario que NO es miembro, debe devolver false")
    void isUserMemberOfOrganization_whenUserIsNotMember_shouldReturnFalse() {
        // GIVEN: Simulamos que la API de GitHub responde con HTTP 404 Not Found.
        stubFor(get(urlEqualTo("/orgs/test-org/members/non-member-user"))
                .willReturn(aResponse()
                        .withStatus(404)));

        // WHEN: Llamamos a nuestro cliente
        boolean isMember = githubClient.isUserMemberOfOrganization("non-member-user", "test-org");

        // THEN: Verificamos que el resultado es el esperado
        assertThat(isMember).isFalse();
    }

    @Test
    @DisplayName("Dado un error 500 de la API, debe devolver false y no lanzar excepción")
    void isUserMemberOfOrganization_whenApiReturnsServerError_shouldReturnFalse() {
        // GIVEN: Simulamos un error interno del servidor.
        stubFor(get(urlEqualTo("/orgs/test-org/members/any-user"))
                .willReturn(aResponse().withStatus(500)));

        // WHEN
        boolean isMember = githubClient.isUserMemberOfOrganization("any-user", "test-org");

        // THEN: Verificamos que el cliente manejó el error y devolvió false.
        assertThat(isMember).isFalse();
    }

    @Test
    @DisplayName("Dado una respuesta 200 OK de la API (no 204), debe devolver false")
    void isUserMemberOfOrganization_whenApiReturnsOk_shouldReturnFalse() {
        // GIVEN: Simulamos que la API de GitHub responde con HTTP 200 OK.
        // Esta es una respuesta exitosa, pero no la que confirma la membresía (que es 204).
        // Este test cubre la rama 'false' de la condición dentro del .map().
        stubFor(get(urlEqualTo("/orgs/test-org/members/any-user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}"))); // Un cuerpo de respuesta genérico

        // WHEN
        boolean isMember = githubClient.isUserMemberOfOrganization("any-user", "test-org");

        // THEN: Verificamos que el cliente interpreta cualquier respuesta exitosa que no sea 204 como 'no miembro'.
        assertThat(isMember).isFalse();
    }
}