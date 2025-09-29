package org.grubhart.pucp.tesis.module_collector.github;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunDto;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.grubhart.pucp.tesis.module_domain.GithubPullRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
        String jsonBody = "[{\"id\":1,\"state\":\"open\",\"merged_at\":null,\"created_at\":\"2024-01-01T00:00:00Z\",\"updated_at\":\"2024-01-01T00:00:00Z\", \"closed_at\":null}]";
        mockWebServer.enqueue(new MockResponse().setBody(jsonBody).addHeader("Content-Type", "application/json"));

        // Act
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests("owner", "repo", since);

        // Assert
        assertEquals(1, pullRequests.size());
        assertEquals(1L, pullRequests.get(0).getId());
    }

    @Test
    void getPullRequests_shouldIncludePrWithNullUpdatedAt() {
        // Arrange
        String jsonBody = "[{\"id\":1,\"state\":\"open\",\"merged_at\":null,\"created_at\":\"2024-01-01T00:00:00Z\",\"updated_at\":null, \"closed_at\":null}]";
        mockWebServer.enqueue(new MockResponse().setBody(jsonBody).addHeader("Content-Type", "application/json"));

        // Act
        List<GithubPullRequestDto> pullRequests = githubClient.getPullRequests("owner", "repo", since);

        // Assert
        assertEquals(1, pullRequests.size());
        assertEquals(1L, pullRequests.get(0).getId());
        assertNull(pullRequests.get(0).getUpdatedAt());
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
}
