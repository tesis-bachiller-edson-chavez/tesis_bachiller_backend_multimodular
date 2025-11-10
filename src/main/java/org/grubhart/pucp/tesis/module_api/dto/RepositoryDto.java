package org.grubhart.pucp.tesis.module_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO que representa un repositorio configurado en el sistema.
 */
@Schema(description = "Información de un repositorio configurado")
public record RepositoryDto(
        @Schema(description = "ID único del repositorio en la base de datos", example = "1")
        Long id,

        @Schema(description = "URL del repositorio en GitHub", example = "https://github.com/user/repo")
        String repositoryUrl,

        @Schema(description = "Nombre del servicio en Datadog asociado al repositorio (puede ser null)", example = "tesis-backend")
        String datadogServiceName,

        @Schema(description = "Propietario del repositorio extraído de la URL", example = "user")
        String owner,

        @Schema(description = "Nombre del repositorio extraído de la URL", example = "repo")
        String repoName,

        @Schema(description = "Nombre del archivo de workflow de deployment en GitHub Actions (puede ser null)", example = "deploy.yml")
        String deploymentWorkflowFileName
) {
}