package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * DTO que representa una métrica DORA en un día específico.
 * Usado para construir series de tiempo diarias.
 */
@Schema(description = "Métrica DORA de un día específico")
public record DailyMetricDto(
        @Schema(description = "Fecha del día", example = "2025-11-11")
        LocalDate date,

        @Schema(description = "Lead time promedio del día en horas (null si no hay datos)", example = "12.5")
        Double averageLeadTimeHours,

        @Schema(description = "Número de deployments en el día", example = "3")
        Long deploymentCount,

        @Schema(description = "Número de commits desplegados en el día", example = "8")
        Long commitCount,

        @Schema(description = "Número de deployments con fallos en el día", example = "0")
        Long failedDeploymentCount,

        @Schema(description = "MTTR promedio del día en horas (null si no hay incidentes resueltos)", example = "2.5")
        Double averageMTTRHours,

        @Schema(description = "Número de incidentes resueltos en el día", example = "2")
        Long resolvedIncidentCount
) {
}
