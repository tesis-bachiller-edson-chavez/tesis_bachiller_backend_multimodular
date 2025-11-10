package org.grubhart.pucp.tesis.module_collector.datadog;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grubhart.pucp.tesis.module_collector.datadog.dto.DatadogFieldValue;
import org.grubhart.pucp.tesis.module_collector.datadog.dto.DatadogIncidentFields;
import org.grubhart.pucp.tesis.module_collector.datadog.dto.DatadogIncidentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DatadogIncidentClientTest {

    private MockWebServer mockWebServer;
    private DatadogIncidentClient datadogClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey = "test-api-key";
    private final String applicationKey = "test-app-key";

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = String.format("http://localhost:%s", mockWebServer.getPort());

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("DD-API-KEY", apiKey)
                .defaultHeader("DD-APPLICATION-KEY", applicationKey)
                .build();

        datadogClient = new DatadogIncidentClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("GIVEN valid API credentials WHEN fetching incidents THEN it should include authentication headers")
    void shouldIncludeAuthenticationHeaders() throws InterruptedException {
        // Given
        String jsonResponse = """
                {
                  "data": [],
                  "meta": {
                    "pagination": {
                      "offset": 0,
                      "size": 0
                    }
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);

        // When
        datadogClient.getIncidents(since);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("DD-API-KEY")).isEqualTo(apiKey);
        assertThat(request.getHeader("DD-APPLICATION-KEY")).isEqualTo(applicationKey);
    }

    @Test
    @DisplayName("GIVEN a since timestamp WHEN fetching incidents THEN it should include the filter in query params")
    void shouldIncludeSinceFilterInQueryParams() throws InterruptedException {
        // Given
        String jsonResponse = """
                {
                  "data": [],
                  "meta": {
                    "pagination": {
                      "offset": 0,
                      "size": 0
                    }
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.parse("2025-01-01T00:00:00Z");

        // When
        datadogClient.getIncidents(since);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("filter%5Bsince%5D"); // URL-encoded: filter[since]
        assertThat(request.getPath()).contains("2025-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("GIVEN a serviceName WHEN fetching incidents THEN it should include the service filter in query params")
    void shouldIncludeServiceFilterInQueryParams() throws InterruptedException {
        // Given
        String jsonResponse = """
                {
                  "data": [],
                  "meta": {
                    "pagination": {
                      "offset": 0,
                      "size": 0
                    }
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.parse("2025-01-01T00:00:00Z");
        String serviceName = "tesis-backend";

        // When
        datadogClient.getIncidents(since, serviceName);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("filter%5Bsince%5D"); // URL-encoded: filter[since]
        assertThat(request.getPath()).contains("2025-01-01T00:00:00Z");
        assertThat(request.getPath()).contains("filter%5Bquery%5D"); // URL-encoded: filter[query]
        assertThat(request.getPath()).contains("service:tesis-backend"); // service:tesis-backend (: is valid in URLs)
    }

    @Test
    @DisplayName("GIVEN null serviceName WHEN fetching incidents THEN it should not include the service filter")
    void shouldNotIncludeServiceFilterWhenNull() throws InterruptedException {
        // Given
        String jsonResponse = """
                {
                  "data": [],
                  "meta": {
                    "pagination": {
                      "offset": 0,
                      "size": 0
                    }
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.parse("2025-01-01T00:00:00Z");

        // When
        datadogClient.getIncidents(since, null);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("filter%5Bsince%5D");
        assertThat(request.getPath()).doesNotContain("filter%5Bquery%5D");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t"})
    @DisplayName("GIVEN a blank serviceName WHEN fetching incidents THEN it should not include the service filter")
    void shouldNotIncludeServiceFilterWhenBlank(String blankServiceName) throws InterruptedException {
        // Given
        String jsonResponse = """
                {
                  "data": [],
                  "meta": {
                    "pagination": {
                      "offset": 0,
                      "size": 0
                    }
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.parse("2025-01-01T00:00:00Z");

        // When
        datadogClient.getIncidents(since, blankServiceName);

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).contains("filter%5Bsince%5D");
        assertThat(request.getPath()).doesNotContain("filter%5Bquery%5D");
    }

    @Test
    @DisplayName("GIVEN DataDog returns incidents WHEN fetching THEN it should deserialize the response correctly")
    void shouldDeserializeIncidentsResponse() {
        // Given
        String jsonResponse = """
                {
                  "data": [
                    {
                      "id": "incident-123",
                      "type": "incidents",
                      "attributes": {
                        "title": "Database connection timeout",
                        "customer_impact_scope": "all",
                        "created": "2025-01-01T12:00:00Z",
                        "modified": "2025-01-01T14:00:00Z",
                        "resolved": "2025-01-01T14:00:00Z",
                        "state": "resolved",
                        "severity": "SEV-2",
                        "fields": {
                          "state": {
                            "value": "resolved"
                          },
                          "severity": {
                            "value": "SEV-2"
                          }
                        }
                      }
                    },
                    {
                      "id": "incident-456",
                      "type": "incidents",
                      "attributes": {
                        "title": "API rate limit exceeded",
                        "customer_impact_scope": "partial",
                        "created": "2025-01-02T10:00:00Z",
                        "modified": "2025-01-02T11:00:00Z",
                        "resolved": null,
                        "state": "active",
                        "severity": "SEV-3",
                        "fields": {
                          "state": {
                            "value": "active"
                          },
                          "severity": {
                            "value": "SEV-3"
                          }
                        }
                      }
                    }
                  ],
                  "meta": {
                    "pagination": {
                      "offset": 0,
                      "size": 2
                    }
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);

        // When
        DatadogIncidentResponse response = datadogClient.getIncidents(since);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.data()).hasSize(2);

        // Verify first incident
        assertThat(response.data().get(0).id()).isEqualTo("incident-123");
        assertThat(response.data().get(0).attributes().title()).isEqualTo("Database connection timeout");
        assertThat(response.data().get(0).attributes().state()).isEqualTo("resolved");
        assertThat(response.data().get(0).attributes().severity()).isEqualTo("SEV-2");
        assertThat(response.data().get(0).attributes().resolved()).isNotNull();

        // Verify second incident
        assertThat(response.data().get(1).id()).isEqualTo("incident-456");
        assertThat(response.data().get(1).attributes().title()).isEqualTo("API rate limit exceeded");
        assertThat(response.data().get(1).attributes().state()).isEqualTo("active");
        assertThat(response.data().get(1).attributes().resolved()).isNull();

        // Verify metadata
        assertThat(response.meta().pagination().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("GIVEN DataDog returns empty response WHEN fetching THEN it should return empty data list")
    void shouldHandleEmptyResponse() {
        // Given
        String jsonResponse = """
                {
                  "data": [],
                  "meta": {
                    "pagination": {
                      "offset": 0,
                      "size": 0
                    }
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);

        // When
        DatadogIncidentResponse response = datadogClient.getIncidents(since);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.data()).isEmpty();
    }

    @Test
    @DisplayName("GIVEN DataDog API returns error WHEN fetching THEN it should throw exception")
    void shouldThrowExceptionOnApiError() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"errors\":[\"Invalid API key\"]}"));

        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);

        // When & Then
        try {
            datadogClient.getIncidents(since);
            assertThat(false).as("Should have thrown exception for 401 error").isTrue();
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
    }

    @Test
    @DisplayName("GIVEN API returns empty body WHEN fetching incidents THEN it should return empty list")
    void shouldReturnNullWhenApiReturnsEmptyBody() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("")); // Empty body

        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);

        // When
        DatadogIncidentResponse response = datadogClient.getIncidents(since);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.data()).isNotNull().isEmpty(); // Pagination returns empty list for empty body
    }

    @Test
    @DisplayName("GIVEN API returns response with null data WHEN fetching incidents THEN it should return empty list")
    void shouldHandleResponseWithNullData() {
        // Given
        String jsonResponse = """
                {
                  "data": null,
                  "meta": {
                    "pagination": {
                      "offset": 0,
                      "size": 0
                    }
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse)
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);

        // When
        DatadogIncidentResponse response = datadogClient.getIncidents(since);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.data()).isNotNull().isEmpty(); // Pagination returns empty list instead of null
    }

    @Test
    @DisplayName("GIVEN JSON with unknown properties for DatadogFieldValue WHEN deserializing THEN it should be ignored")
    void shouldIgnoreUnknownPropertiesWhenDeserializingFieldValue() {
        // Given
        String json = """
            {
              "value": "some-value",
              "type": "text",
              "unknown_property": "should-be-ignored"
            }
            """;

        // When & Then
        assertDoesNotThrow(() -> {
            DatadogFieldValue fieldValue = objectMapper.readValue(json, DatadogFieldValue.class);
            assertThat(fieldValue).isNotNull();
            assertThat(fieldValue.value()).isEqualTo("some-value");
        });
    }

    @Test
    @DisplayName("GIVEN JSON with unknown properties for DatadogIncidentFields WHEN deserializing THEN it should be ignored")
    void shouldIgnoreUnknownPropertiesWhenDeserializingIncidentFields() {
        // Given
        String json = """
            {
              "state": { "value": "resolved" },
              "severity": { "value": "SEV-2" },
              "new_field_from_datadog": "some-new-value"
            }
            """;

        // When & Then
        assertDoesNotThrow(() -> {
            DatadogIncidentFields fields = objectMapper.readValue(json, DatadogIncidentFields.class);
            assertThat(fields).isNotNull();
            assertThat(fields.state().value()).isEqualTo("resolved");
            assertThat(fields.severity().value()).isEqualTo("SEV-2");
        });
    }

    @Test
    @DisplayName("GIVEN Datadog returns multiple pages WHEN fetching incidents THEN should fetch all pages and consolidate results")
    void shouldFetchAllPagesOfIncidents() throws InterruptedException {
        // Given - First page with 100 incidents
        StringBuilder firstPageJson = new StringBuilder("{\"data\": [");
        for (int i = 0; i < 100; i++) {
            if (i > 0) firstPageJson.append(",");
            firstPageJson.append(String.format("""
                {
                  "id": "incident-%d",
                  "type": "incidents",
                  "attributes": {
                    "title": "Incident %d",
                    "customer_impact_scope": "none",
                    "created": "2025-01-01T12:00:00Z",
                    "modified": "2025-01-01T12:00:00Z",
                    "resolved": null,
                    "state": "active",
                    "severity": "SEV-5"
                  }
                }
                """, i, i));
        }
        firstPageJson.append("], \"meta\": {\"pagination\": {\"offset\": 0, \"size\": 100}}}");

        // Second page with 50 incidents (last page)
        StringBuilder secondPageJson = new StringBuilder("{\"data\": [");
        for (int i = 100; i < 150; i++) {
            if (i > 100) secondPageJson.append(",");
            secondPageJson.append(String.format("""
                {
                  "id": "incident-%d",
                  "type": "incidents",
                  "attributes": {
                    "title": "Incident %d",
                    "customer_impact_scope": "none",
                    "created": "2025-01-01T12:00:00Z",
                    "modified": "2025-01-01T12:00:00Z",
                    "resolved": null,
                    "state": "active",
                    "severity": "SEV-5"
                  }
                }
                """, i, i));
        }
        secondPageJson.append("], \"meta\": {\"pagination\": {\"offset\": 100, \"size\": 50}}}");

        mockWebServer.enqueue(new MockResponse()
                .setBody(firstPageJson.toString())
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setBody(secondPageJson.toString())
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.parse("2025-01-01T00:00:00Z");

        // When
        DatadogIncidentResponse response = datadogClient.getIncidents(since);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.data()).hasSize(150); // All incidents from both pages

        // Verify both requests were made
        RecordedRequest request1 = mockWebServer.takeRequest();
        assertThat(request1.getPath()).contains("page%5Boffset%5D=0");
        assertThat(request1.getPath()).contains("page%5Bsize%5D=100");

        RecordedRequest request2 = mockWebServer.takeRequest();
        assertThat(request2.getPath()).contains("page%5Boffset%5D=100");
        assertThat(request2.getPath()).contains("page%5Bsize%5D=100");
    }

    @Test
    @DisplayName("GIVEN Datadog returns less than page size WHEN fetching incidents THEN should stop pagination")
    void shouldStopPaginationWhenPageNotFull() throws InterruptedException {
        // Given - Single page with only 30 incidents (less than page size of 100)
        StringBuilder jsonResponse = new StringBuilder("{\"data\": [");
        for (int i = 0; i < 30; i++) {
            if (i > 0) jsonResponse.append(",");
            jsonResponse.append(String.format("""
                {
                  "id": "incident-%d",
                  "type": "incidents",
                  "attributes": {
                    "title": "Incident %d",
                    "customer_impact_scope": "none",
                    "created": "2025-01-01T12:00:00Z",
                    "modified": "2025-01-01T12:00:00Z",
                    "resolved": null,
                    "state": "active",
                    "severity": "SEV-5"
                  }
                }
                """, i, i));
        }
        jsonResponse.append("], \"meta\": {\"pagination\": {\"offset\": 0, \"size\": 30}}}");

        mockWebServer.enqueue(new MockResponse()
                .setBody(jsonResponse.toString())
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.parse("2025-01-01T00:00:00Z");

        // When
        DatadogIncidentResponse response = datadogClient.getIncidents(since);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.data()).hasSize(30);

        // Verify only one request was made (no second page fetch)
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("GIVEN pagination with service filter WHEN fetching incidents THEN should include service filter in all pages")
    void shouldIncludeServiceFilterInAllPaginatedRequests() throws InterruptedException {
        // Given - Two pages
        String page1Json = """
            {
              "data": [
                {"id": "inc-1", "type": "incidents", "attributes": {"title": "Test 1", "customer_impact_scope": "none", "created": "2025-01-01T12:00:00Z", "modified": "2025-01-01T12:00:00Z", "state": "active", "severity": "SEV-5"}}
              ],
              "meta": {"pagination": {"offset": 0, "size": 1}}
            }
            """;

        // Make page 1 return exactly 100 items to trigger pagination
        StringBuilder fullPage1 = new StringBuilder("{\"data\": [");
        for (int i = 0; i < 100; i++) {
            if (i > 0) fullPage1.append(",");
            fullPage1.append(String.format("""
                {"id": "inc-%d", "type": "incidents", "attributes": {"title": "Test %d", "customer_impact_scope": "none", "created": "2025-01-01T12:00:00Z", "modified": "2025-01-01T12:00:00Z", "state": "active", "severity": "SEV-5"}}
                """, i, i));
        }
        fullPage1.append("], \"meta\": {\"pagination\": {\"offset\": 0, \"size\": 100}}}");

        String page2Json = """
            {
              "data": [
                {"id": "inc-100", "type": "incidents", "attributes": {"title": "Test 100", "customer_impact_scope": "none", "created": "2025-01-01T12:00:00Z", "modified": "2025-01-01T12:00:00Z", "state": "active", "severity": "SEV-5"}}
              ],
              "meta": {"pagination": {"offset": 100, "size": 1}}
            }
            """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(fullPage1.toString())
                .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
                .setBody(page2Json)
                .addHeader("Content-Type", "application/json"));

        Instant since = Instant.parse("2025-01-01T00:00:00Z");
        String serviceName = "my-service";

        // When
        DatadogIncidentResponse response = datadogClient.getIncidents(since, serviceName);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.data()).hasSize(101);

        // Verify both requests included the service filter
        RecordedRequest request1 = mockWebServer.takeRequest();
        assertThat(request1.getPath()).contains("filter%5Bquery%5D");
        assertThat(request1.getPath()).contains("service:my-service");

        RecordedRequest request2 = mockWebServer.takeRequest();
        assertThat(request2.getPath()).contains("filter%5Bquery%5D");
        assertThat(request2.getPath()).contains("service:my-service");
    }
}
