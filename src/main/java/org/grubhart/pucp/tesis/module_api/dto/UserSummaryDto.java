package org.grubhart.pucp.tesis.module_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "Información resumida de un usuario activo de la organización")
public record UserSummaryDto(
        @Schema(description = "Nombre de usuario de GitHub", example = "john_doe")
        String githubUsername,

        @Schema(description = "Nombre completo del usuario", example = "John Doe")
        String name,

        @Schema(description = "URL del avatar del usuario en GitHub", example = "https://avatars.githubusercontent.com/u/123456")
        String avatarUrl,

        @Schema(description = "Roles asignados al usuario en el sistema", example = "[\"DEVELOPER\", \"TECH_LEAD\"]")
        Set<String> roles
) {
}
