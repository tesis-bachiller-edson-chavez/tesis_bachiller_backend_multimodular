package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Response wrapper for Datadog APM Service Dependencies API.
 * Maps to the JSON response from GET /api/v1/service_dependencies
 *
 * The response can be either a map of service names to dependencies,
 * or an object with various fields. This flexible structure handles both cases.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogApmServicesResponse(
        Map<String, DatadogApmServiceDependency> services
) {
}
