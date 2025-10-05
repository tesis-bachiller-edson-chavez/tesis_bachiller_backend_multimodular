package org.grubhart.pucp.tesis.module_collector.github;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunDto;
import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunsResponse;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.GithubPullRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.springframework.http.ResponseEntity;


class GithubClientImplTest {

    private MockWebServer mockWebServer;
    private GithubClientImpl githubClient;
    private final LocalDateTime since = LocalDateTime.of(2024, 1, 1, 0, 0);

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        githubClient = new GithubClientImpl(WebClient.builder(), baseUrl, "fake-token");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getCommits_shouldReturnCommitsWhenResponseIsSuccessful() {
        // Arrange
        String jsonBody = "[{\"sha\":\"123\",\"commit\":{\"author\":{\"name\":\"test\",\"email\":\"test@test.com\",\"date\":\"2024-01-01T00:00:00Z\"},\"message\":\"feat: test commit\"},\"html_url\":\"http://test.com\"}]";
        mockWebServer.enqueue(new MockResponse().setBody(jsonBody).addHeader("Content-Type", "application/json"));

        // Act
        List<GithubCommitDto> commits = githubClient.getCommits("owner", "repo", since);

        // Assert
        assertEquals(1, commits.size());
        assertEquals("123", commits.get(0).getSha());
    }

    @Test
    void getPullRequests_shouldReturnPullRequestsWhenResponseIsSuccessful() {
        // Arrange
        String jsonBody = "[{\"id\":1, \"number\":101, \"state\":\"open\",\"merged_at\":null,\"created_at\":\"2024-01-01T00:00:00Z\",\"updated_at\":\"2024-01-01T00:00:00Z\", \"closed_at\":null}]";
        mockWebServer.enqueue(new MockResponse().setBody(jsonBody).addHeader("Content-Type", "application/json"));

        String firstCommitJsonBody = "[{\"sha\":\"abcdef123456\"}]";
        mockWebServer.enqueue(new MockResponse().setBody(firstCommitJsonBody).addHeader("Content-Type", "application/json"));

        // Act
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests("owner", "repo", since);

        // Assert
        assertEquals(1, pullRequests.size());
        assertEquals(1L, pullRequests.get(0).getId());
        assertEquals("abcdef123456", pullRequests.get(0).getFirstCommitSha());
    }

    @Test
    void getPullRequests_shouldIncludePrWithNullUpdatedAt() {
        // Arrange
        String jsonBody = "[{\"id\":1, \"number\":101, \"state\":\"open\",\"merged_at\":null,\"created_at\":\"2024-01-01T00:00:00Z\",\"updated_at\":null, \"closed_at\":null}]";
        mockWebServer.enqueue(new MockResponse().setBody(jsonBody).addHeader("Content-Type", "application/json"));

        String firstCommitJsonBody = "[{\"sha\":\"abcdef123456\"}]";
        mockWebServer.enqueue(new MockResponse().setBody(firstCommitJsonBody).addHeader("Content-Type", "application/json"));

        // Act
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests("owner", "repo", since);

        // Assert
        assertEquals(1, pullRequests.size());
        assertEquals(1L, pullRequests.get(0).getId());
        assertNull(pullRequests.get(0).getUpdatedAt());
        assertEquals("abcdef123456", pullRequests.get(0).getFirstCommitSha());
    }

    @Test
    void getWorkflowRuns_shouldReturnWorkflowRunsWhenResponseIsSuccessful() {
        // Arrange
        String jsonBody = "{\"total_count\":1,\"workflow_runs\":[{\"id\":1,\"status\":\"completed\",\"conclusion\":\"success\",\"created_at\":\"2024-01-01T00:00:00Z\",\"updated_at\":\"2024-01-01T00:00:00Z\"}]}";
        mockWebServer.enqueue(new MockResponse().setBody(jsonBody).addHeader("Content-Type", "application/json"));

        // Act
        List<GitHubWorkflowRunDto> runs = githubClient.getWorkflowRuns("owner", "repo", "main.yml", since);

        // Assert
        assertEquals(1, runs.size());
        assertEquals(1L, runs.get(0).getId());
    }

    @Test
    void getWorkflowRuns_shouldThrowRuntimeExceptionWhenApiReturns5xxError() {
        // Arrange
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            githubClient.getWorkflowRuns("owner", "repo", "main.yml", since);
        });
    }

    @Test
    void getWorkflowRuns_shouldReturnEmptyListWhenApiReturns4xxError() {
        // Arrange
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        // Act
        List<GitHubWorkflowRunDto> runs = githubClient.getWorkflowRuns("owner", "repo", "main.yml", since);

        // Assert
        assertTrue(runs.isEmpty());
    }

    @Test
    void getWorkflowRuns_shouldReturnEmptyListWhenWorkflowRunsFieldIsMissing() {
        // Arrange
        String jsonBody = "{\"total_count\": 0}";
        mockWebServer.enqueue(new MockResponse().setBody(jsonBody).addHeader("Content-Type", "application/json"));

        // Act
        List<GitHubWorkflowRunDto> runs = githubClient.getWorkflowRuns("owner", "repo", "main.yml", since);

        // Assert
        assertTrue(runs.isEmpty());
    }

    @Test
    void parseNextPageUrl_shouldHandleAllCases() {
        // Case 1: Null header
        assertNull(githubClient.parseNextPageUrl(null));

        // Case 2: Empty header list
        assertNull(githubClient.parseNextPageUrl(Collections.emptyList()));

        // Case 3: Header without "next" rel
        assertNull(githubClient.parseNextPageUrl(Collections.singletonList("<http://example.com/page=2>; rel=\"last\"")));

        // Case 4: Valid "next" link
        assertEquals("http://example.com/page=2", githubClient.parseNextPageUrl(Collections.singletonList("<http://example.com/page=2>; rel=\"next\"")));

        // Case 5: Valid "next" link with messy spacing
        assertEquals("http://example.com/page=2", githubClient.parseNextPageUrl(Collections.singletonList("  <http://example.com/page=2>  ;  rel=\"next\"  ")));

        // Case 6: Complex header with multiple links
        assertEquals("http://example.com/page=3", githubClient.parseNextPageUrl(Collections.singletonList("<http://example.com/page=1>; rel=\"first\", <http://example.com/page=3>; rel=\"next\", <http://example.com/page=5>; rel=\"last\"")));

        // Case 7: Malformed link segment (no rel)
        assertNull(githubClient.parseNextPageUrl(Collections.singletonList("<https://api.github.com/malformed-url>")));

        // Case 8: Malformed URL (no starting '<')
        assertNull(githubClient.parseNextPageUrl(Collections.singletonList("https://api.github.com/malformed-url>; rel=\"next\"")));

        // Case 9: Malformed URL (no ending '>')
        assertNull(githubClient.parseNextPageUrl(Collections.singletonList("<https://api.github.com/malformed-url; rel=\"next\"")));
    }

    @Test
    void getWorkflowRuns_shouldCorrectlyFilterByDateAndHandleNullTimestamps() {
        // Arrange
        String jsonBody = "{\"total_count\":3,\"workflow_runs\":[{\"id\":1,\"status\":\"completed\",\"conclusion\":\"success\",\"created_at\":\"2023-12-31T23:59:59Z\",\"updated_at\":\"2023-12-31T23:59:59Z\"},{\"id\":2,\"status\":\"completed\",\"conclusion\":\"success\",\"created_at\":\"2024-01-01T00:00:00Z\",\"updated_at\":\"2024-01-01T00:00:00Z\"},{\"id\":3,\"status\":\"completed\",\"conclusion\":\"success\",\"created_at\":null,\"updated_at\":\"2024-01-02T00:00:00Z\"}]}";
        mockWebServer.enqueue(new MockResponse().setBody(jsonBody).addHeader("Content-Type", "application/json"));

        // Act
        List<GitHubWorkflowRunDto> runs = githubClient.getWorkflowRuns("owner", "repo", "main.yml", since);

        // Assert
        assertEquals(2, runs.size());
        assertTrue(runs.stream().anyMatch(run -> run.getId() == 2L));
        assertTrue(runs.stream().anyMatch(run -> run.getId() == 3L));
        assertFalse(runs.stream().anyMatch(run -> run.getId() == 1L));
    }

    @Test
    void getWorkflowRuns_shouldHandleSinglePageResponseWithoutLinkHeader() {
        // Arrange
        String jsonBody = "{\"total_count\":1,\"workflow_runs\":[{\"id\":1,\"status\":\"completed\",\"conclusion\":\"success\",\"created_at\":\"2024-01-01T00:00:00Z\",\"updated_at\":\"2024-01-01T00:00:00Z\"}]}";
        mockWebServer.enqueue(
            new MockResponse()
                .setBody(jsonBody)
                .addHeader("Content-Type", "application/json")
        );

        // Act
        List<GitHubWorkflowRunDto> runs = githubClient.getWorkflowRuns("owner", "repo", "main.yml", since);

        // Assert
        assertEquals(1, runs.size());
        assertEquals(1L, runs.get(0).getId());
    }

    @Test
    void getWorkflowRuns_stopsPagingWhenNoLinkHeaderIsPresent() throws Exception {
        // Arrange
        String jsonBody = "{\"total_count\":1,\"workflow_runs\":[{\"id\":1,\"status\":\"completed\",\"conclusion\":\"success\",\"created_at\":\"2024-01-01T00:00:00Z\",\"updated_at\":\"2024-01-01T00:00:00Z\"}]}";
        mockWebServer.enqueue(
            new MockResponse()
                .setBody(jsonBody)
                .addHeader("Content-Type", "application/json")
        );

        // Act
        githubClient.getWorkflowRuns("owner", "repo", "main.yml", since);

        // Assert
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    void parseNextPageUrl_shouldHandleNullInLinkHeaderList() {
        // Arrange
        List<String> linkHeaders = new ArrayList<>();
        linkHeaders.add(null);
        linkHeaders.add("<http://example.com/page=3>; rel=\"next\"");

        // Act
        String nextPageUrl = githubClient.parseNextPageUrl(linkHeaders);

        // Assert
        assertEquals("http://example.com/page=3", nextPageUrl);
    }

    @Test
    void parseNextPageUrl_shouldReturnNullWhenLinkHeaderListContainsOnlyNull() {
        // Arrange
        List<String> linkHeaders = new ArrayList<>();
        linkHeaders.add(null);

        // Act
        String nextPageUrl = githubClient.parseNextPageUrl(linkHeaders);

        // Assert
        assertNull(nextPageUrl);
    }

    @Test
    @DisplayName("getPullRequests debe detenerse si la respuesta de la API es nula")
    void getPullRequests_shouldStopWhenResponseIsNull() {
        // 1. Arrange
        // Creamos un mock de WebClient y su cadena de llamadas
        WebClient mockWebClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        // Configuramos la cadena de mocks para que devuelva un Mono que resulta en 'null'
        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        // La clave está aquí: toEntityList(...).block() devolverá null
        when(responseSpec.toEntityList(GithubPullRequestDto.class)).thenReturn(Mono.justOrEmpty(null));

        // Creamos una instancia del cliente con nuestro WebClient mockeado
        GithubClientImpl clientWithMock = new GithubClientImpl(mockWebClient);

        // 2. Act
        List<GithubPullRequestDto> pullRequests = clientWithMock.getPullRequests("owner", "repo", since);

        // 3. Assert
        // Verificamos que la lista de PRs está vacía, ya que el bucle se rompió en la primera iteración.
        assertThat(pullRequests).isNotNull();
        assertThat(pullRequests).isEmpty();

        // Verificamos que el método get() del webclient fue llamado una vez, y no más.
        verify(mockWebClient, times(1)).get();
    }

    @Test
    @DisplayName("getPullRequests debe detenerse si el cuerpo de la respuesta de la API es nulo")
    void getPullRequests_shouldStopWhenResponseBodyIsNull() {
        // 1. Arrange
        // Creamos un mock de WebClient y su cadena de llamadas
        WebClient mockWebClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        // Creamos una ResponseEntity mockeada
        ResponseEntity<List<GithubPullRequestDto>> mockResponseEntity = mock(ResponseEntity.class);
        // La clave está aquí: getBody() devolverá null
        when(mockResponseEntity.getBody()).thenReturn(null);
        // Devolvemos cabeceras vacías para que el parseo de la paginación no falle
        when(mockResponseEntity.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());

        // Configuramos la cadena de mocks para que devuelva nuestra ResponseEntity mockeada
        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntityList(GithubPullRequestDto.class)).thenReturn(Mono.just(mockResponseEntity));

        // Creamos una instancia del cliente con nuestro WebClient mockeado
        GithubClientImpl clientWithMock = new GithubClientImpl(mockWebClient);

        // 2. Act
        List<GithubPullRequestDto> pullRequests = clientWithMock.getPullRequests("owner", "repo", since);

        // 3. Assert
        // Verificamos que la lista de PRs está vacía, ya que el bucle se rompió.
        assertThat(pullRequests).isNotNull();
        assertThat(pullRequests).isEmpty();

        // Verificamos que el método get() del webclient fue llamado una vez, y no más.
        verify(mockWebClient, times(1)).get();
    }

    @Test
    @DisplayName("getWorkflowRuns debe detenerse si la respuesta de la API es nula")
    void getWorkflowRuns_shouldStopWhenResponseIsNull() {
        // 1. Arrange
        // Creamos un mock de WebClient y su cadena de llamadas
        WebClient mockWebClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        // Configuramos la cadena de mocks para que devuelva un Mono que resulta en 'null'
        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        // La clave está aquí: toEntity(...).block() devolverá null
        when(responseSpec.toEntity(GitHubWorkflowRunsResponse.class)).thenReturn(Mono.justOrEmpty(null));

        // Creamos una instancia del cliente con nuestro WebClient mockeado
        GithubClientImpl clientWithMock = new GithubClientImpl(mockWebClient);

        // 2. Act
        List<GitHubWorkflowRunDto> workflowRuns = clientWithMock.getWorkflowRuns("owner", "repo", "workflow.yml", since);

        // 3. Assert
        // Verificamos que la lista de runs está vacía, ya que el bucle se rompió en la primera iteración.
        assertThat(workflowRuns).isNotNull();
        assertThat(workflowRuns).isEmpty();

        // Verificamos que el método get() del webclient fue llamado una vez, y no más.
        verify(mockWebClient, times(1)).get();
    }

    @Test
    @DisplayName("getWorkflowRuns debe detenerse si la respuesta de la API no tiene cuerpo")
    void getWorkflowRuns_shouldStopWhenResponseHasNoBody() {
        // 1. Arrange
        // Creamos un mock de WebClient y su cadena de llamadas
        WebClient mockWebClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        // Creamos una ResponseEntity mockeada
        ResponseEntity<GitHubWorkflowRunsResponse> mockResponseEntity = mock(ResponseEntity.class);

        // La clave está aquí: hasBody() devolverá false
        when(mockResponseEntity.hasBody()).thenReturn(false);

        // Configuramos la cadena de mocks para que devuelva nuestra ResponseEntity mockeada
        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntity(GitHubWorkflowRunsResponse.class)).thenReturn(Mono.just(mockResponseEntity));

        // Creamos una instancia del cliente con nuestro WebClient mockeado
        GithubClientImpl clientWithMock = new GithubClientImpl(mockWebClient);

        // 2. Act
        List<GitHubWorkflowRunDto> workflowRuns = clientWithMock.getWorkflowRuns("owner", "repo", "workflow.yml", since);

        // 3. Assert
        // Verificamos que la lista de runs está vacía, ya que el bucle se rompió.
        assertThat(workflowRuns).isNotNull();
        assertThat(workflowRuns).isEmpty();

        // Verificamos que el método get() del webclient fue llamado una vez, y no más.
        verify(mockWebClient, times(1)).get();
    }


    @Test
    @DisplayName("getCommits debe detenerse si la respuesta de la API es nula")
    void getCommits_shouldStopWhenResponseIsNull() {
        // 1. Arrange
        // Creamos un mock de WebClient y su cadena de llamadas
        WebClient mockWebClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        // Configuramos la cadena de mocks
        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);

        // La clave: toEntityList(...).block() devolverá null
        when(responseSpec.toEntityList(GithubCommitDto.class)).thenReturn(Mono.justOrEmpty(null));

        // Creamos una instancia del cliente con nuestro WebClient mockeado
        GithubClientImpl clientWithMock = new GithubClientImpl(mockWebClient);

        // 2. Act
        List<GithubCommitDto> commits = clientWithMock.getCommits("owner", "repo", since);

        // 3. Assert
        // Verificamos que la lista de commits está vacía, ya que el bucle se rompió.
        assertThat(commits).isNotNull();
        assertThat(commits).isEmpty();

        // Verificamos que el método get() del webclient fue llamado una vez, y no más.
        verify(mockWebClient, times(1)).get();
    }

    @Test
    @DisplayName("getCommits debe detenerse si el cuerpo de la respuesta de la API es nulo")
    void getCommits_shouldStopWhenResponseBodyIsNull() {
        // 1. Arrange
        // Creamos un mock de WebClient y su cadena de llamadas
        WebClient mockWebClient = mock(WebClient.class);
        WebClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        // Creamos una ResponseEntity mockeada
        ResponseEntity<List<GithubCommitDto>> mockResponseEntity = mock(ResponseEntity.class);

        // La clave está aquí: getBody() devolverá null
        when(mockResponseEntity.getBody()).thenReturn(null);
        // Devolvemos cabeceras vacías para que el parseo de la paginación no falle
        when(mockResponseEntity.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());

        // Configuramos la cadena de mocks para que devuelva nuestra ResponseEntity mockeada
        when(mockWebClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toEntityList(GithubCommitDto.class)).thenReturn(Mono.just(mockResponseEntity));

        // Creamos una instancia del cliente con nuestro WebClient mockeado
        GithubClientImpl clientWithMock = new GithubClientImpl(mockWebClient);

        // 2. Act
        List<GithubCommitDto> commits = clientWithMock.getCommits("owner", "repo", since);

        // 3. Assert
        // Verificamos que la lista de commits está vacía, ya que el bucle se rompió.
        assertThat(commits).isNotNull();
        assertThat(commits).isEmpty();

        // Verificamos que el método get() del webclient fue llamado una vez, y no más.
        verify(mockWebClient, times(1)).get();
    }
    
    @Test
    @DisplayName("getPullRequest debe registrar un error si falla la obtención del primer commit")
    void getPullRequest_whenFirstCommitFails_shouldLogError() {
        // 1. Arrange
        // Capturador de logs
        Logger logger = (Logger) LoggerFactory.getLogger(GithubClientImpl.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        // Espía del cliente
        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());
        GithubClientImpl clientSpy = spy(new GithubClientImpl(WebClient.builder(), baseUrl, "fake-token"));

        // Simular la respuesta de la API para la lista de PRs
        String prJsonBody = "[{\"id\":1, \"number\":123, \"state\":\"open\", \"updated_at\":\"2024-05-20T10:00:00Z\"}]";
        mockWebServer.enqueue(new MockResponse().setBody(prJsonBody).addHeader("Content-Type", "application/json"));

        // Forzar la excepción en el método interno
        String errorMessage = "Error simulado en la obtención del commit";
        doThrow(new RuntimeException(errorMessage)).when(clientSpy).getFirstCommitShaForPr("owner", "repo", 123);

        // 2. Act
        clientSpy.getPullRequests("owner", "repo", since);

        // 3. Assert
        // Filtrar los eventos de log para obtener solo los de nivel ERROR
        List<ILoggingEvent> errorLogs = listAppender.list.stream()
                .filter(event -> "ERROR".equals(event.getLevel().toString()))
                .collect(Collectors.toList());

        // Verificar que se registró exactamente un error
        assertEquals(1, errorLogs.size(), "Se esperaba exactamente un mensaje de error.");

        // Verificar el contenido del mensaje de error
        ILoggingEvent errorEvent = errorLogs.get(0);
        String expectedMessage = "Failed to fetch first commit for PR #123 in owner/repo. Error: " + errorMessage;
        assertTrue(errorEvent.getFormattedMessage().contains(expectedMessage),
                "El mensaje de error no contiene el texto esperado. Mensaje real: " + errorEvent.getFormattedMessage());

        // Limpieza
        logger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("fetchCommitsForPr debe devolver nulo si el Mono de la petición está vacío")
    void fetchCommitsForPr_whenRequestMonoIsEmpty_shouldReturnNull() {
        // Arrange
        GithubClientImpl clientSpy = spy(githubClient);
        // Forzamos al método interno a devolver un Mono vacío
        doReturn(Mono.empty()).when(clientSpy).createCommitsRequestMono(anyString(), anyString(), anyInt());

        // Act
        // Llamamos directamente al método que queremos probar
        List<GithubCommitDto> result = clientSpy.fetchCommitsForPr("owner", "repo", 123);

        // Assert
        // Verificamos que el resultado es nulo, porque .block() en un Mono vacío devuelve null
        assertNull(result);
    }

    @Test
    @DisplayName("getFirstCommitShaForPr debe lanzar una excepción si fetchCommitsForPr devuelve una lista vacía")
    void getFirstCommitShaForPr_whenFetchReturnsEmptyList_shouldThrowException() {
        // Arrange
        GithubClientImpl clientSpy = spy(githubClient);
        // Forzamos al método fetch a devolver una lista vacía
        doReturn(Collections.emptyList()).when(clientSpy).fetchCommitsForPr(anyString(), anyString(), anyInt());

        // Act & Assert
        // Verificamos que se lanza la excepción esperada cuando se llama al método principal
        Exception exception = assertThrows(RuntimeException.class, () -> {
            clientSpy.getFirstCommitShaForPr("owner", "repo", 123);
        });

        String expectedMessage = "No commits found for PR";
        String actualMessage = exception.getMessage();

        assertTrue(actualMessage.contains(expectedMessage));
    }
}
