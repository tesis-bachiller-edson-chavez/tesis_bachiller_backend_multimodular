package org.grubhart.pucp.tesis.module_collector.github;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;

class GithubClientImplTest {

    // Para la mayoría de las pruebas, usaremos un servidor real simulado.

    private MockWebServer mockWebServer;
    private GithubClientImpl githubClient;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Se crea una nueva instancia del servidor para CADA prueba.
        mockWebServer = new MockWebServer(); // Inicializa el servidor
        mockWebServer.start(); // Inicia el servidor en un puerto aleatorio

        String baseUrl = mockWebServer.url("/").toString(); // Obtenemos la URL base del servidor mock

        // 4. Creamos el cliente a probar.
        githubClient = new GithubClientImpl(WebClient.builder(), baseUrl, "test-token");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("getCommits debe obtener todos los commits de múltiples páginas")
    void getCommits_shouldFetchAllCommitsFromMultiplePages() throws InterruptedException {
        // Arrange
        // Página 1: Contiene dos commits y un enlace a la página 2.
        String nextPageUrl = mockWebServer.url("/repos/owner/repo/commits?page=2").toString();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("Link", String.format("<%s>; rel=\"next\"", nextPageUrl))
                .setBody("[{\"sha\": \"sha1\"}, {\"sha\": \"sha2\"}]")
        );

        // Página 2: Contiene un commit y no tiene enlace 'next'.
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[{\"sha\": \"sha3\"}]")
        );

        // Act
        List<GithubCommitDto> commits = githubClient.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));

        // Verificamos que se hayan recolectado los commits de ambas páginas.
        assertEquals(3, commits.size(), "Se deben recolectar los commits de todas las páginas.");
        assertEquals("sha1", commits.get(0).getSha());
        assertEquals("sha2", commits.get(1).getSha());
        assertEquals("sha3", commits.get(2).getSha());

        // Verificamos que se hicieron dos peticiones al servidor.
        assertEquals(2, mockWebServer.getRequestCount());

        // Verificamos la primera petición.
        RecordedRequest request1 = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request1);
        assertTrue(request1.getPath().contains("/repos/owner/repo/commits?since="));

        // Verificamos la segunda petición.
        RecordedRequest request2 = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request2);
        assertEquals("/repos/owner/repo/commits?page=2", request2.getPath());
    }

    @Test
    @DisplayName("getCommits: Debe manejar una respuesta con cuerpo nulo sin fallar")
    void getCommits_whenResponseHasNullBody_shouldNotFail() {
        // Arrange: Simulamos una respuesta del servidor que es válida (200 OK) pero tiene un cuerpo nulo.
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
        );

        // Act: Ejecutamos el método a probar.
        List<GithubCommitDto> commits = githubClient.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));

        // Assert: Verificamos que el método no lanzó una excepción y devolvió una lista vacía.
        // Esto prueba que la condición `response.getBody() != null` funcionó correctamente.
        assertTrue(commits.isEmpty(), "La lista de commits debería estar vacía cuando el cuerpo de la respuesta es nulo.");
    }

    @Test
    @DisplayName("getCommits: Debe manejar un error 500 del servidor sin fallar")
    void getCommits_whenServerReturns500_shouldNotFail() {
        // Arrange: Simulamos un error 500 del servidor
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // Act
        List<GithubCommitDto> commits = githubClient.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));

        // Assert
        // Verificamos que el método manejó el error y devolvió una lista vacía.
        // Esto prueba que el `onErrorReturn` que envuelve la llamada a `toEntity` funciona,
        // y el bucle termina de forma segura.
        assertTrue(commits.isEmpty(), "La lista de commits debería estar vacía cuando el servidor devuelve un error.");
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("getCommits: Debe manejar una respuesta nula del WebClient sin fallar")
    void getCommits_whenWebClientReturnsNullResponse_shouldNotFail() throws Exception {
        // Arrange: Para forzar que la variable 'response' sea null, necesitamos
        // simular que la llamada reactiva `.block()` devuelve null.
        // Usamos Mockito para crear un WebClient falso que se comporte como queremos.

        WebClient mockWebClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        Mono<ResponseEntity<List<GithubCommitDto>>> mono = mock(Mono.class);

        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(any(ParameterizedTypeReference.class))).thenReturn(mono);
        when(mono.doOnError(any())).thenReturn(mono);
        when(mono.onErrorReturn(any())).thenReturn(mono);
        // Esta es la línea clave: forzamos que .block() devuelva null.
        when(mono.block()).thenReturn(null);

        // Usamos reflexión para inyectar nuestro WebClient mockeado en la instancia bajo prueba.
        Field webClientField = GithubClientImpl.class.getDeclaredField("webClient");
        webClientField.setAccessible(true);
        webClientField.set(githubClient, mockWebClient);

        // Act & Assert: La prueba es exitosa si no se lanza un NullPointerException.
        assertDoesNotThrow(() -> githubClient.getCommits("owner", "repo", LocalDateTime.now()));
    }

    @Test
    @DisplayName("parseNextPageUrl: Debe manejar correctamente la cabecera Link")
    void parseNextPageUrl_shouldHandleLinkHeaderCorrectly() {
        // Arrange: El método ahora tiene visibilidad de paquete, por lo que podemos llamarlo directamente.
        String expectedUrl = "https://api.github.com/resource?page=2";
        List<String> linkHeaderWithNext = List.of(String.format("<%s>; rel=\"next\", <https://api.github.com/resource?page=3>; rel=\"last\"", expectedUrl));
        List<String> linkHeaderWithoutNext = List.of("<https://api.github.com/resource?page=1>; rel=\"first\"");

        // Act & Assert
        assertNull(githubClient.parseNextPageUrl(null), "Debe devolver null para una cabecera nula.");
        assertNull(githubClient.parseNextPageUrl(List.of()), "Debe devolver null para una cabecera vacía.");
        assertNull(githubClient.parseNextPageUrl(linkHeaderWithoutNext), "Debe devolver null si no hay rel='next'.");
        assertEquals(expectedUrl, githubClient.parseNextPageUrl(linkHeaderWithNext), "Debe extraer la URL 'next' correctamente.");
    }
}