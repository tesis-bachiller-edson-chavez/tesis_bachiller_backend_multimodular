package org.grubhart.pucp.tesis.module_collector.datadog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response DTO for Datadog Service Catalog API
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogServiceResponse(
        List<DatadogService> data
) {
}
