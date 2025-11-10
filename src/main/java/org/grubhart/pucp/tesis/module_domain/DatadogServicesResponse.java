package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response wrapper for Datadog Services API.
 * Maps to the JSON response from GET /api/v2/services/definitions
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogServicesResponse(
        List<DatadogServiceData> data
) {
}
