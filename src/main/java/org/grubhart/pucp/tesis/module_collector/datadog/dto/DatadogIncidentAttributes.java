package org.grubhart.pucp.tesis.module_collector.datadog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogIncidentAttributes(
        String title,
        @JsonProperty("customer_impact_scope") String customerImpactScope,
        Instant created,
        Instant modified,
        Instant resolved,
        String state,
        String severity,
        DatadogIncidentFields fields
) {
}
