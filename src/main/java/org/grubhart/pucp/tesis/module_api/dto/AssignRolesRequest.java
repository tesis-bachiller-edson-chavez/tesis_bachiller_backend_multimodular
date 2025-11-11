package org.grubhart.pucp.tesis.module_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

@Schema(description = "Request para asignar roles a un usuario")
public record AssignRolesRequest(
        @Schema(
                description = "Lista de roles a asignar al usuario. Roles válidos: ADMIN, ENGINEERING_MANAGER, TECH_LEAD, DEVELOPER. Esta operación reemplaza todos los roles existentes del usuario.",
                example = "[\"TECH_LEAD\", \"DEVELOPER\"]",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotEmpty(message = "Debe proporcionar al menos un rol")
        Set<String> roles
) {
}
