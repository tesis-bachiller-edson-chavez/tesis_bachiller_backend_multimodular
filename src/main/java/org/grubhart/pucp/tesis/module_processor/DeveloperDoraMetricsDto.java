package org.grubhart.pucp.tesis.module_processor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO que contiene las métricas DORA aplicables a nivel de developer individual.
 *
 * Nota: MTTR y CFR no son aplicables a nivel individual ya que son métricas
 * de servicio/equipo. Solo se incluyen Lead Time y Deployment Frequency.
 */
@Schema(description = "Métricas DORA específicas del developer")
public record DeveloperDoraMetricsDto(
        @Schema(description = "Tiempo promedio desde commit hasta deployment en producción (en horas)",
                example = "24.5")
        Double averageLeadTimeHours,

        @Schema(description = "Número total de deployments que incluyen commits del developer",
                example = "45")
        Long deploymentCount,

        @Schema(description = "Número de commits del developer que han sido desplegados a producción",
                example = "120")
        Long deploymentCommitCount,

        @Schema(description = "Tiempo mínimo de lead time registrado (en horas)",
                example = "2.5")
        Double minLeadTimeHours,

        @Schema(description = "Tiempo máximo de lead time registrado (en horas)",
                example = "168.0")
        Double maxLeadTimeHours
) {
}
