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
     * Fetches all service definitions from Datadog Service Catalog.
     *
     * Note: Service Catalog is a separate feature that requires manual service registration.
     * If you want to list services from APM/traces automatically, you may need to use
     * a different endpoint or approach.
     *
     * @return DatadogServiceResponse containing the list of services
     */
    public DatadogServiceResponse getServices() {
        String endpoint = "/api/v2/services/definitions";
        logger.info("Fetching services from Datadog Service Catalog: {}", endpoint);

        try {
            // Log the raw response as String first for debugging
            String rawResponse = webClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(error -> logger.error("Error fetching services from Datadog", error))
                    .block();

            logger.info("Raw Datadog API response length: {} bytes",
                    rawResponse != null ? rawResponse.length() : 0);
            logger.debug("Raw Datadog API response: {}", rawResponse);

            if (rawResponse == null || rawResponse.isEmpty()) {
                logger.warn("Received null or empty response from Datadog Service Catalog");
                return new DatadogServiceResponse(null);
            }

            // Now try to parse it
            DatadogServiceResponse response = webClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .bodyToMono(DatadogServiceResponse.class)
                    .block();

            int serviceCount = response != null && response.data() != null ? response.data().size() : 0;
            logger.info("Successfully fetched {} services from Datadog Service Catalog", serviceCount);

            if (serviceCount == 0) {
                logger.warn("No services found in Datadog Service Catalog. " +
                        "The Service Catalog requires manual service registration. " +
                        "Services with APM traces are not automatically included here. " +
                        "Consider registering services manually or entering service names directly.");
            }

            return response != null ? response : new DatadogServiceResponse(null);
        } catch (Exception e) {
            logger.error("Failed to fetch services from Datadog API. " +
                    "Verify your API key and Application key have the correct permissions.", e);
            throw e;
        }
    }
}
