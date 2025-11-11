package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO que contiene todas las métricas del dashboard para el rol Developer.
 * Incluye información de repositorios, commits, pull requests y métricas DORA.
 */
@Schema(description = "Métricas completas del dashboard para un Developer")
public record DeveloperMetricsResponse(
        @Schema(description = "Nombre de usuario del developer en GitHub", example = "john_doe")
        String developerUsername,

        @Schema(description = "Lista de repositorios en los que el developer ha participado")
        List<RepositoryStatsDto> repositories,

        @Schema(description = "Estadísticas agregadas de commits del developer")
        CommitStatsDto commitStats,

        @Schema(description = "Estadísticas agregadas de pull requests del developer")
        PullRequestStatsDto pullRequestStats,

        @Schema(description = "Métricas DORA del developer (Lead Time, Deployment Frequency)")
        DeveloperDoraMetricsDto doraMetrics
) {
}
