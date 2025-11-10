package org.grubhart.pucp.tesis.module_collector.datadog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Attributes of a Datadog service
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogServiceAttributes(
        String name,
        @JsonProperty("schema_version")
        String schemaVersion
) {
}
