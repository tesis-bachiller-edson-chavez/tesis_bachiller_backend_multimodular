package org.grubhart.pucp.tesis.module_collector.datadog;

import org.grubhart.pucp.tesis.module_collector.datadog.dto.DatadogServiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Client for interacting with the Datadog Service Catalog API.
 * Uses WebClient to fetch service definitions from Datadog.
 */
@Component
public class DatadogServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(DatadogServiceClient.class);
    private final WebClient webClient;

    public DatadogServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${datadog.base-url:https://us5.datadoghq.com}") String baseUrl,
            @Value("${datadog.api-key}") String apiKey,
            @Value("${datadog.application-key}") String applicationKey) {

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("DD-API-KEY", apiKey)
                .defaultHeader("DD-APPLICATION-KEY", applicationKey)
                .build();
        logger.info("DatadogServiceClient initialized with base URL: {}", baseUrl);
    }

    /**
     * Fetches all service definitions from Datadog Service Catalog
     *
     * @return DatadogServiceResponse containing the list of services
     */
    public DatadogServiceResponse getServices() {
        logger.debug("Fetching services from Datadog Service Catalog");

        try {
            DatadogServiceResponse response = webClient.get()
                    .uri("/api/v2/services/definitions")
                    .retrieve()
                    .bodyToMono(DatadogServiceResponse.class)
                    .doOnError(error -> logger.error("Error fetching services from Datadog", error))
                    .block();

            int serviceCount = response != null && response.data() != null ? response.data().size() : 0;
            logger.info("Successfully fetched {} services from Datadog", serviceCount);

            return response;
        } catch (Exception e) {
            logger.error("Failed to fetch services from Datadog API", e);
            throw e;
        }
    }
}
