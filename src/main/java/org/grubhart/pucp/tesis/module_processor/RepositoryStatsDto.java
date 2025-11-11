package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO que contiene estadísticas de un repositorio específico para un developer.
 */
@Schema(description = "Estadísticas de un repositorio para un developer")
public record RepositoryStatsDto(
        @Schema(description = "ID del repositorio", example = "1")
        Long repositoryId,

        @Schema(description = "Nombre del repositorio", example = "backend-api")
        String repositoryName,

        @Schema(description = "URL del repositorio en GitHub", example = "https://github.com/org/backend-api")
        String repositoryUrl,

        @Schema(description = "Número de commits realizados por el developer en este repositorio", example = "42")
        Long commitCount
) {
}
