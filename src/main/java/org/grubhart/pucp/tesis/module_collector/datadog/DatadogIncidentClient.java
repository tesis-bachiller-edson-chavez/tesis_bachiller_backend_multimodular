package org.grubhart.pucp.tesis.module_collector.datadog;

import org.grubhart.pucp.tesis.module_collector.datadog.dto.DatadogIncidentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Client for interacting with the Datadog Incidents API.
 * Uses WebClient to fetch incident data for DORA metrics calculation (MTTR, CFR).
 */
@Component
public class DatadogIncidentClient {

    private static final Logger logger = LoggerFactory.getLogger(DatadogIncidentClient.class);
    private final WebClient webClient;

    @Autowired
    public DatadogIncidentClient(
            WebClient.Builder webClientBuilder,
            @Value("${datadog.base-url:https://us5.datadoghq.com}") String baseUrl,
            @Value("${datadog.api-key}") String apiKey,
            @Value("${datadog.application-key}") String applicationKey) {

        this(buildWebClient(webClientBuilder, baseUrl, apiKey, applicationKey));
        logger.info("DatadogIncidentClient initialized with base URL: {}", baseUrl);
    }

    // Package-private constructor for testing
    DatadogIncidentClient(WebClient webClient) {
        this.webClient = webClient;
    }

    private static WebClient buildWebClient(
            WebClient.Builder webClientBuilder,
            String baseUrl,
            String apiKey,
            String applicationKey) {
        return webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("DD-API-KEY", apiKey)
                .defaultHeader("DD-APPLICATION-KEY", applicationKey)
                .build();
    }

    /**
     * Fetches incidents from Datadog created or modified since the specified timestamp.
     *
     * @param since The timestamp to filter incidents (only incidents created/modified after this time)
     * @return DatadogIncidentResponse containing the list of incidents and metadata
     */
    public DatadogIncidentResponse getIncidents(Instant since) {
        return getIncidents(since, null);
    }

    /**
     * Fetches incidents from Datadog created or modified since the specified timestamp,
     * optionally filtered by service name.
     *
     * @param since The timestamp to filter incidents (only incidents created/modified after this time)
     * @param serviceName The Datadog service name to filter by (optional, can be null)
     * @return DatadogIncidentResponse containing the list of incidents and metadata
     */
    public DatadogIncidentResponse getIncidents(Instant since, String serviceName) {
        logger.debug("Fetching incidents from Datadog since: {} for service: {}", since, serviceName);

        String formattedSince = DateTimeFormatter.ISO_INSTANT.format(since);
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/v2/incidents")
                .queryParam("filter[since]", formattedSince);

        // Add service filter if provided
        if (serviceName != null && !serviceName.isBlank()) {
            // Datadog uses query parameter to filter by service tag
            uriBuilder.queryParam("filter[query]", "service:" + serviceName);
        }

        String uri = uriBuilder.build().toUriString();

        try {
            DatadogIncidentResponse response = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(DatadogIncidentResponse.class)
                    .doOnError(error -> logger.error("Error fetching incidents from Datadog", error))
                    .block();

            int incidentCount = response != null && response.data() != null ? response.data().size() : 0;
            logger.info("Successfully fetched {} incidents from Datadog", incidentCount);

            return response;
        } catch (Exception e) {
            logger.error("Failed to fetch incidents from Datadog API", e);
            throw e;
        }
    }
}
