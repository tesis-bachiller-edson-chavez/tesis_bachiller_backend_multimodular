package org.grubhart.pucp.tesis.module_collector.datadog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogIncidentResponse(
        List<DatadogIncidentData> data,
        DatadogMeta meta
) {
}
