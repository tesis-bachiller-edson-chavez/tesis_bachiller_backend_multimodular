package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO que contiene todas las métricas del dashboard para el rol Engineering Manager.
 * Incluye métricas agregadas de toda la organización (o equipos filtrados)
 * y estadísticas individuales de cada equipo.
 */
@Schema(description = "Métricas completas del dashboard para un Engineering Manager")
public record EngineeringManagerMetricsResponse(
        @Schema(description = "Nombre de usuario del engineering manager en GitHub", example = "em_user")
        String engineeringManagerUsername,

        @Schema(description = "Número total de equipos incluidos en las métricas", example = "3")
        Integer totalTeams,

        @Schema(description = "Número total de desarrolladores en los equipos incluidos", example = "15")
        Integer totalDevelopers,

        @Schema(description = "Estadísticas individuales de cada equipo")
        List<TeamMetricsDto> teams,

        @Schema(description = "Lista de todos los repositorios en los que los equipos han participado")
        List<RepositoryStatsDto> repositories,

        @Schema(description = "Estadísticas agregadas de commits de todos los equipos")
        CommitStatsDto aggregatedCommitStats,

        @Schema(description = "Estadísticas agregadas de pull requests de todos los equipos")
        PullRequestStatsDto aggregatedPullRequestStats,

        @Schema(description = "Métricas DORA agregadas de todos los equipos (Lead Time, Deployment Frequency, CFR, MTTR)")
        TeamDoraMetricsDto aggregatedDoraMetrics
) {
}