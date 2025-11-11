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
 * - MTTR no se incluye (métrica de servicio, no individual)
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

        // === Series de Tiempo Diarias ===

        @Schema(description = "Serie de tiempo con métricas DORA por día")
        List<DailyMetricDto> dailyMetrics
) {
}
