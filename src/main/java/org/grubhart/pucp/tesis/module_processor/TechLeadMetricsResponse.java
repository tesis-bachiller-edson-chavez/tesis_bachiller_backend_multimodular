package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO que contiene todas las métricas del dashboard para el rol Tech Lead.
 * Incluye métricas agregadas del equipo y estadísticas individuales de cada miembro.
 */
@Schema(description = "Métricas completas del dashboard para un Tech Lead")
public record TechLeadMetricsResponse(
        @Schema(description = "Nombre de usuario del tech lead en GitHub", example = "tech_lead_user")
        String techLeadUsername,

        @Schema(description = "ID del equipo", example = "1")
        Long teamId,

        @Schema(description = "Nombre del equipo", example = "Backend Team")
        String teamName,

        @Schema(description = "Estadísticas individuales de cada miembro del equipo")
        List<TeamMemberStatsDto> teamMembers,

        @Schema(description = "Lista de repositorios en los que el equipo ha participado")
        List<RepositoryStatsDto> repositories,

        @Schema(description = "Estadísticas agregadas de commits del equipo")
        CommitStatsDto commitStats,

        @Schema(description = "Estadísticas agregadas de pull requests del equipo")
        PullRequestStatsDto pullRequestStats,

        @Schema(description = "Métricas DORA del equipo (Lead Time, Deployment Frequency, CFR, MTTR)")
        TeamDoraMetricsDto doraMetrics
) {
}
