package org.grubhart.pucp.tesis.module_api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

@Schema(description = "Información completa del usuario autenticado")
public record UserDto(
        @Schema(description = "ID interno del usuario en el sistema", example = "1")
        Long id,

        @Schema(description = "ID del usuario en GitHub", example = "123456")
        Long githubId,

        @Schema(description = "Nombre de usuario de GitHub", example = "john_doe")
        String githubUsername,

        @Schema(description = "Email del usuario", example = "john.doe@example.com")
        String email,

        @Schema(description = "Roles asignados al usuario en el sistema", example = "[\"DEVELOPER\", \"TECH_LEAD\"]")
        Set<String> roles
) {}