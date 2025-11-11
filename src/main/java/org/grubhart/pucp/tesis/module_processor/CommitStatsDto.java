package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * DTO que contiene estadísticas agregadas de commits de un developer.
 */
@Schema(description = "Estadísticas agregadas de commits de un developer")
public record CommitStatsDto(
        @Schema(description = "Número total de commits realizados", example = "156")
        Long totalCommits,

        @Schema(description = "Número de repositorios en los que ha participado", example = "5")
        Long repositoryCount,

        @Schema(description = "Fecha del commit más reciente", example = "2025-11-11T10:30:00")
        LocalDateTime lastCommitDate,

        @Schema(description = "Fecha del primer commit", example = "2025-01-15T08:00:00")
        LocalDateTime firstCommitDate
) {
}
