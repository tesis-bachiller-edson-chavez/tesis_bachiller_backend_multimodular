package org.grubhart.pucp.tesis.module_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO para actualizar la configuración de un repositorio.
 */
@Schema(description = "Datos para actualizar un repositorio")
public record UpdateRepositoryRequest(
        @Schema(description = "Nombre del servicio en Datadog (puede ser null para eliminar la asociación)", example = "tesis-backend", nullable = true)
        String datadogServiceName
) {
}