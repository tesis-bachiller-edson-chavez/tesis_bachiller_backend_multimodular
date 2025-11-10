package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a service and its dependencies from Datadog APM.
 * Maps to the response from GET /api/v1/service_dependencies
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogApmServiceDependency(
        String name,
        @JsonProperty("called_by")
        List<String> calledBy,
        List<String> calls
) {
}
