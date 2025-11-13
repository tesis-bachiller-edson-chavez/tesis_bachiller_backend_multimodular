package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO que contiene las métricas DORA completas para un developer individual.
 * Incluye valores agregados y series de tiempo diarias.
 *
 * DORA metrics:
 * - Lead Time for Changes: Tiempo desde commit hasta producción
 * - Deployment Frequency: Frecuencia de deployments
 * - Change Failure Rate (CFR): Tasa de fallos en deployments
 * - Mean Time To Recovery (MTTR): Tiempo promedio de recuperación de incidentes
 */
@Schema(description = "Métricas DORA completas del developer con series de tiempo")
public record DeveloperDoraMetricsDto(
        // === Valores Agregados ===

        @Schema(description = "Tiempo promedio desde commit hasta deployment en producción (en horas)",
                example = "24.5")
        Double averageLeadTimeHours,

        @Schema(description = "Tiempo mínimo de lead time registrado (en horas)",
                example = "2.5")
        Double minLeadTimeHours,

        @Schema(description = "Tiempo máximo de lead time registrado (en horas)",
                example = "168.0")
        Double maxLeadTimeHours,

        @Schema(description = "Número total de deployments que incluyen commits del developer",
                example = "45")
        Long totalDeploymentCount,

        @Schema(description = "Número de commits del developer que han sido desplegados a producción",
                example = "120")
        Long deployedCommitCount,

        @Schema(description = "Change Failure Rate: Porcentaje de deployments que causaron incidentes (0-100)",
                example = "15.5")
        Double changeFailureRate,

        @Schema(description = "Número de deployments del developer que causaron incidentes",
                example = "7")
        Long failedDeploymentCount,

        @Schema(description = "MTTR: Tiempo promedio de recuperación de incidentes en horas (null si no hay incidentes resueltos)",
                example = "2.5")
        Double averageMTTRHours,

        @Schema(description = "Tiempo mínimo de recuperación de incidentes registrado (en horas)",
                example = "0.5")
        Double minMTTRHours,

        @Schema(description = "Tiempo máximo de recuperación de incidentes registrado (en horas)",
                example = "8.0")
        Double maxMTTRHours,

        @Schema(description = "Número total de incidentes resueltos",
                example = "5")
        Long totalResolvedIncidents,

        // === Series de Tiempo Diarias ===

        @Schema(description = "Serie de tiempo con métricas DORA por día")
        List<DailyMetricDto> dailyMetrics
) {
}
