package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO que contiene estadísticas de un miembro del equipo.
 * Incluye información básica del miembro y sus métricas agregadas.
 */
@Schema(description = "Estadísticas de un miembro del equipo")
public record TeamMemberStatsDto(
        @Schema(description = "ID del usuario", example = "1")
        Long userId,

        @Schema(description = "Nombre de usuario en GitHub", example = "john_doe")
        String githubUsername,

        @Schema(description = "Nombre completo del usuario", example = "John Doe")
        String name,

        @Schema(description = "Email del usuario", example = "john@example.com")
        String email,

        @Schema(description = "Cantidad total de commits del miembro")
        long totalCommits,

        @Schema(description = "Cantidad total de pull requests del miembro")
        long totalPullRequests,

        @Schema(description = "Cantidad de pull requests mergeados")
        long mergedPullRequests,

        @Schema(description = "Lead time promedio en horas del miembro")
        Double averageLeadTimeHours,

        @Schema(description = "Cantidad de deployments del miembro")
        long deploymentCount
) {
}
