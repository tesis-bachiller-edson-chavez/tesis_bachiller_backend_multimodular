package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a single service entry in the Datadog Services API response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogServiceData(
        String id,
        String type,
        DatadogServiceAttributes attributes
) {
}
