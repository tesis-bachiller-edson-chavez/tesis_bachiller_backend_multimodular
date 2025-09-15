package org.grubhart.pucp.tesis.module_collector.github;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.GithubPullRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GithubClientImplTest {

    private MockWebServer mockWebServer;
    private GithubClientImpl githubClient;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString();
        githubClient = new GithubClientImpl(WebClient.builder(), baseUrl, "test-token");
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("getPullRequests debe detener la paginación cuando encuentra un PR antiguo")
    void getPullRequests_shouldStopPaginatingWhenOldPRIsFound() throws InterruptedException {
        LocalDateTime since = LocalDateTime.now().minusDays(5);
        String recentDate1 = LocalDateTime.now().minusDays(1).format(DateTimeFormatter.ISO_DATE_TIME);
        String recentDate2 = LocalDateTime.now().minusDays(2).format(DateTimeFormatter.ISO_DATE_TIME);
        String oldDate = since.minusDays(1).format(DateTimeFormatter.ISO_DATE_TIME);

        String page2Url = mockWebServer.url("/repos/owner/repo/pulls?page=2").toString();
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").addHeader("Link", String.format("<%s>; rel=\"next\"", page2Url)).setBody(String.format("[{\"id\": 1, \"updated_at\": \"%s\"}]", recentDate1)));

        String page3Url = mockWebServer.url("/repos/owner/repo/pulls?page=3").toString();
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").addHeader("Link", String.format("<%s>; rel=\"next\"", page3Url)).setBody(String.format("[{\"id\": 2, \"updated_at\": \"%s\"}, {\"id\": 3, \"updated_at\": \"%s\"}]", recentDate2, oldDate)));

        mockWebServer.enqueue(new MockResponse().setBody("[{\"id\": 4}]"));

        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests("owner", "repo", since);

        assertEquals(2, pullRequests.size());
        assertEquals(1L, pullRequests.get(0).getId());
        assertEquals(2L, pullRequests.get(1).getId());
        assertEquals(2, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("getPullRequests: Debe manejar un error 500 del servidor sin fallar")
    void getPullRequests_whenServerReturns500_shouldNotFail() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests("owner", "repo", LocalDateTime.now().minusDays(1));
        assertTrue(pullRequests.isEmpty());
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("getPullRequests: Debe manejar una respuesta con cuerpo nulo sin fallar")
    void getPullRequests_whenResponseHasNullBody_shouldNotFail() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json"));
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests("owner", "repo", LocalDateTime.now().minusDays(1));
        assertTrue(pullRequests.isEmpty());
    }

    @Test
    @DisplayName("getPullRequests: Debe manejar un PR con updated_at nulo sin fallar")
    void getPullRequests_shouldHandleNullUpdatedAtGracefully() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody("[{\"id\": 123}]"));
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests("owner", "repo", LocalDateTime.now());
        assertEquals(1, pullRequests.size());
        assertEquals(123L, pullRequests.get(0).getId());
        assertNull(pullRequests.get(0).getUpdatedAt());
    }

    @Test
    @DisplayName("getCommits debe obtener todos los commits de múltiples páginas")
    void getCommits_shouldFetchAllCommitsFromMultiplePages() throws InterruptedException {
        String nextPageUrl = mockWebServer.url("/repos/owner/repo/commits?page=2").toString();
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").addHeader("Link", String.format("<%s>; rel=\"next\"", nextPageUrl)).setBody("[{\"sha\": \"sha1\"}, {\"sha\": \"sha2\"}]"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody("[{\"sha\": \"sha3\"}]"));
        List<GithubCommitDto> commits = githubClient.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));
        assertEquals(3, commits.size());
        assertEquals("sha1", commits.get(0).getSha());
        assertEquals("sha2", commits.get(1).getSha());
        assertEquals("sha3", commits.get(2).getSha());
        assertEquals(2, mockWebServer.getRequestCount());
        RecordedRequest request1 = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request1);
        assertTrue(request1.getPath().contains("/repos/owner/repo/commits?since="));
        RecordedRequest request2 = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request2);
        assertEquals("/repos/owner/repo/commits?page=2", request2.getPath());
    }

    @Test
    @DisplayName("getCommits: Debe manejar una respuesta con cuerpo nulo sin fallar")
    void getCommits_whenResponseHasNullBody_shouldNotFail() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json"));
        List<GithubCommitDto> commits = githubClient.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));
        assertTrue(commits.isEmpty());
    }

    @Test
    @DisplayName("getCommits: Debe manejar un error 500 del servidor sin fallar")
    void getCommits_whenServerReturns500_shouldNotFail() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        List<GithubCommitDto> commits = githubClient.getCommits("owner", "repo", LocalDateTime.now().minusDays(1));
        assertTrue(commits.isEmpty());
        assertEquals(1, mockWebServer.getRequestCount());
    }

    @Test
    @DisplayName("parseNextPageUrl: Debe manejar correctamente la cabecera Link")
    void parseNextPageUrl_shouldHandleLinkHeaderCorrectly() {
        String expectedUrl = "https://api.github.com/resource?page=2";
        List<String> linkHeaderWithNext = List.of(String.format("<%s>; rel=\"next\", <https://api.github.com/resource?page=3>; rel=\"last\"", expectedUrl));
        List<String> linkHeaderWithoutNext = List.of("<https://api.github.com/resource?page=1>; rel=\"first\"");

        assertNull(githubClient.parseNextPageUrl(null));
        assertNull(githubClient.parseNextPageUrl(List.of()));
        assertNull(githubClient.parseNextPageUrl(linkHeaderWithoutNext));
        assertEquals(expectedUrl, githubClient.parseNextPageUrl(linkHeaderWithNext));
    }
}
