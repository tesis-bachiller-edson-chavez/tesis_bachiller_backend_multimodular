package org.grubhart.pucp.tesis.module_collector.datadog;

import org.grubhart.pucp.tesis.module_domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Client for interacting with the Datadog APM Service Dependencies API.
 * Uses WebClient to fetch services from Datadog APM.
 */
@Component
public class DatadogServiceClient implements DatadogServiceCollector {

    private static final Logger logger = LoggerFactory.getLogger(DatadogServiceClient.class);
    private final WebClient webClient;
    private final String environment;

    public DatadogServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${datadog.base-url:https://us5.datadoghq.com}") String baseUrl,
            @Value("${datadog.api-key}") String apiKey,
            @Value("${datadog.application-key}") String applicationKey,
            @Value("${datadog.environment:prod}") String environment) {

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("DD-API-KEY", apiKey)
                .defaultHeader("DD-APPLICATION-KEY", applicationKey)
                .build();
        this.environment = environment;
        logger.info("DatadogServiceClient initialized with base URL: {} and environment: {}", baseUrl, environment);
    }

    /**
     * Fetches all services from Datadog APM Service Dependencies API.
     *
     * This endpoint retrieves services that have active APM traces in the specified environment.
     * Unlike Service Catalog, this automatically includes all services with APM instrumentation.
     *
     * @return DatadogServicesResponse containing the list of services
     */
    @Override
    public DatadogServicesResponse getServices() {
        String endpoint = "/api/v1/service_dependencies";
        logger.info("Fetching services from Datadog APM for environment: {}", environment);

        try {
            // Log the raw response as String first for debugging
            String rawResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(endpoint)
                            .queryParam("env", environment)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnError(error -> logger.error("Error fetching services from Datadog APM", error))
                    .block();

            logger.info("Raw Datadog APM API response length: {} bytes",
                    rawResponse != null ? rawResponse.length() : 0);
            logger.debug("Raw Datadog APM API response: {}", rawResponse);

            if (rawResponse == null || rawResponse.isEmpty()) {
                logger.warn("Received null or empty response from Datadog APM");
                return new DatadogServicesResponse(null);
            }

            // Parse the APM response which returns a map of service dependencies
            Map<String, Object> apmResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(endpoint)
                            .queryParam("env", environment)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            // Convert APM response to our standard DatadogServicesResponse format
            List<DatadogServiceData> serviceDataList = convertApmResponseToServiceData(apmResponse);

            int serviceCount = serviceDataList.size();
            logger.info("Successfully fetched {} services from Datadog APM (environment: {})", serviceCount, environment);

            if (serviceCount == 0) {
                logger.warn("No services found in Datadog APM for environment '{}'. " +
                        "Verify that services are instrumented with APM and sending traces to this environment.",
                        environment);
            }

            return new DatadogServicesResponse(serviceDataList);
        } catch (Exception e) {
            logger.error("Failed to fetch services from Datadog APM API. " +
                    "Verify your API key, Application key permissions, and that the environment '{}' exists.",
                    environment, e);
            throw e;
        }
    }

    /**
     * Converts the APM service dependencies response to our standard DatadogServiceData format.
     * The APM API returns a map where keys are service names.
     *
     * @param apmResponse the raw APM response map
     * @return list of DatadogServiceData objects
     */
    private List<DatadogServiceData> convertApmResponseToServiceData(Map<String, Object> apmResponse) {
        if (apmResponse == null || apmResponse.isEmpty()) {
            return Collections.emptyList();
        }

        List<DatadogServiceData> services = new ArrayList<>();

        for (Map.Entry<String, Object> entry : apmResponse.entrySet()) {
            String serviceName = entry.getKey();

            // Create a DatadogServiceData object for each service
            DatadogServiceAttributes attributes = new DatadogServiceAttributes(serviceName, "apm-v1");
            DatadogServiceData serviceData = new DatadogServiceData(
                    serviceName,
                    "service",
                    attributes
            );
            services.add(serviceData);
        }

        logger.debug("Converted {} APM services to DatadogServiceData format", services.size());
        return services;
    }
}
