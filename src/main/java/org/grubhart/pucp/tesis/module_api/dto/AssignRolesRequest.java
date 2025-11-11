package org.grubhart.pucp.tesis.module_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

@Schema(description = "Request para asignar roles a un usuario (reemplaza roles existentes)")
public record AssignRolesRequest(
        @Schema(
                description = "Lista de roles a asignar al usuario. IMPORTANTE: Esta operación REEMPLAZA todos los roles existentes. " +
                        "Si el usuario tiene [ADMIN, DEVELOPER] y envías [TECH_LEAD], el usuario quedará solo con [TECH_LEAD]. " +
                        "Para mantener roles existentes, inclúyelos todos en la lista. " +
                        "Roles válidos: ADMIN, ENGINEERING_MANAGER, TECH_LEAD, DEVELOPER.",
                example = "[\"TECH_LEAD\", \"DEVELOPER\"]",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotEmpty(message = "Debe proporcionar al menos un rol")
        Set<String> roles
) {
}
