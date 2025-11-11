package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO que contiene estadísticas agregadas de pull requests de un developer.
 */
@Schema(description = "Estadísticas agregadas de pull requests de un developer")
public record PullRequestStatsDto(
        @Schema(description = "Número total de pull requests creados", example = "23")
        Long totalPullRequests,

        @Schema(description = "Número de pull requests mergeados", example = "20")
        Long mergedPullRequests,

        @Schema(description = "Número de pull requests abiertos actualmente", example = "3")
        Long openPullRequests
) {
}
