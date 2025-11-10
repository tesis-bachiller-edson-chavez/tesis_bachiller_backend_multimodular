package org.grubhart.pucp.tesis.module_collector.datadog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a service from Datadog Service Catalog API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogService(
        String id,
        String type,
        DatadogServiceAttributes attributes
) {
}
