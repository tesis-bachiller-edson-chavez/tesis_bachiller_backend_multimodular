package org.grubhart.pucp.tesis.module_collector.datadog.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DatadogMeta(
        DatadogPagination pagination
) {
}
