package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO que representa las métricas de un equipo individual.
 * Similar a TeamMemberStatsDto pero a nivel de equipo completo.
 * Usado por el Engineering Manager para ver estadísticas de cada equipo.
 */
@Schema(description = "Métricas individuales de un equipo")
public record TeamMetricsDto(
        @Schema(description = "ID del equipo", example = "1")
        Long teamId,

        @Schema(description = "Nombre del equipo", example = "Backend Team")
        String teamName,

        @Schema(description = "Número de miembros en el equipo", example = "5")
        Integer memberCount,

        @Schema(description = "Número total de commits del equipo", example = "120")
        Long totalCommits,

        @Schema(description = "Número de pull requests creados por el equipo", example = "45")
        Long totalPullRequests,

        @Schema(description = "Número de repositorios en los que el equipo ha participado", example = "3")
        Integer repositoryCount,

        @Schema(description = "Estadísticas de commits del equipo")
        CommitStatsDto commitStats,

        @Schema(description = "Estadísticas de pull requests del equipo")
        PullRequestStatsDto pullRequestStats,

        @Schema(description = "Métricas DORA del equipo (Lead Time, Deployment Frequency, CFR, MTTR)")
        TeamDoraMetricsDto doraMetrics,

        @Schema(description = "Lista de repositorios en los que el equipo ha participado")
        List<RepositoryStatsDto> repositories
) {
}
