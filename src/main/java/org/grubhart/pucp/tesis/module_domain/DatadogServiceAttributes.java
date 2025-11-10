package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Attributes of a Datadog service from Service Catalog.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogServiceAttributes(
        String name,
        @JsonProperty("schema_version")
        String schemaVersion
) {
    /**
     * Convenience method for consistency with other parts of the codebase.
     * @return the service name
     */
    public String serviceName() {
        return name;
    }
}
