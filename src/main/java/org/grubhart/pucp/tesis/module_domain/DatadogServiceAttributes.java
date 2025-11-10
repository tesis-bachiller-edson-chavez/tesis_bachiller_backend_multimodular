package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Attributes of a Datadog service definition.
 * Contains the service name and schema version.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogServiceAttributes(
        @JsonProperty("schema-version") String schemaVersion,
        @JsonProperty("service-name") String serviceName
) {
}
