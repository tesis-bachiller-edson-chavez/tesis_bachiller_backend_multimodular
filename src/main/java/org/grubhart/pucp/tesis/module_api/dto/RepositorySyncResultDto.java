package org.grubhart.pucp.tesis.module_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO que representa el resultado de una sincronización de repositorios desde GitHub.
 */
@Schema(description = "Resultado de la sincronización de repositorios desde GitHub")
public record RepositorySyncResultDto(
        @Schema(description = "Cantidad de repositorios nuevos creados", example = "5")
        int newRepositories,

        @Schema(description = "Total de repositorios en la base de datos después de la sincronización", example = "12")
        int totalRepositories,

        @Schema(description = "Cantidad de repositorios que ya existían y no fueron modificados", example = "7")
        int unchanged
) {
}