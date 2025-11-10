package org.grubhart.pucp.tesis.module_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO representing a Datadog service for the frontend
 */
@Schema(description = "Servicio de Datadog")
public record DatadogServiceDto(
        @Schema(description = "Nombre del servicio en Datadog", example = "tesis-backend")
        String name
) {
}
