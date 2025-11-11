package org.grubhart.pucp.tesis.module_api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_processor.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para gestionar equipos de desarrollo y su relación con miembros y repositorios.
 */
@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "API para gestión de equipos de desarrollo, sus miembros (developers y tech leads) y repositorios asignados")
@SecurityRequirement(name = "oauth2")
public class TeamController {

    private final TeamManagementService teamManagementService;

    public TeamController(TeamManagementService teamManagementService) {
        this.teamManagementService = teamManagementService;
    }

    @PostMapping
    @Operation(
            summary = "Crear un nuevo equipo",
            description = "Crea un nuevo equipo con nombre único y opcionalmente asigna tech leads al momento de la creación. Los tech leads deben tener el rol TECH_LEAD y no pueden pertenecer a otro equipo.",
            responses = {
                    @ApiResponse(
                            responseCode = "201",
                            description = "Equipo creado exitosamente",
                            content = @Content(schema = @Schema(implementation = TeamResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Datos de entrada inválidos (nombre duplicado, tech lead no válido, etc.)"
                    )
            }
    )
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        try {
            TeamResponse response = teamManagementService.createTeam(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    @Operation(
            summary = "Listar todos los equipos",
            description = "Obtiene una lista de todos los equipos configurados en el sistema, incluyendo estadísticas de miembros, tech leads y repositorios asignados.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Lista de equipos obtenida exitosamente",
                            content = @Content(schema = @Schema(implementation = TeamResponse.class))
                    )
            }
    )
    public ResponseEntity<List<TeamResponse>> getAllTeams() {
        List<TeamResponse> teams = teamManagementService.getAllTeams();
        return ResponseEntity.ok(teams);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Obtener detalles de un equipo",
            description = "Obtiene información detallada de un equipo específico, incluyendo la lista completa de miembros, tech leads y repositorios asignados.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Detalles del equipo obtenidos exitosamente",
                            content = @Content(schema = @Schema(implementation = TeamDetailResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Equipo no encontrado"
                    )
            }
    )
    public ResponseEntity<TeamDetailResponse> getTeam(
            @Parameter(description = "ID del equipo", required = true)
            @PathVariable Long id) {
        try {
            TeamDetailResponse response = teamManagementService.getTeam(id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Actualizar un equipo",
            description = "Actualiza el nombre y/o la lista de tech leads de un equipo existente. Al actualizar tech leads, los anteriores son removidos y se asignan los nuevos.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Equipo actualizado exitosamente",
                            content = @Content(schema = @Schema(implementation = TeamResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Datos de entrada inválidos (nombre duplicado, tech lead no válido, etc.)"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Equipo no encontrado"
                    )
            }
    )
    public ResponseEntity<TeamResponse> updateTeam(
            @Parameter(description = "ID del equipo", required = true)
            @PathVariable Long id,
            @Valid @RequestBody UpdateTeamRequest request) {
        try {
            TeamResponse response = teamManagementService.updateTeam(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Eliminar un equipo",
            description = "Elimina un equipo del sistema. **IMPORTANTE:** Solo se puede eliminar un equipo que no tenga miembros activos. Primero se deben remover todos los miembros.",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "Equipo eliminado exitosamente"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "No se puede eliminar el equipo porque tiene miembros activos"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Equipo no encontrado"
                    )
            }
    )
    public ResponseEntity<Void> deleteTeam(
            @Parameter(description = "ID del equipo", required = true)
            @PathVariable Long id) {
        try {
            teamManagementService.deleteTeam(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/members")
    @Operation(
            summary = "Listar miembros de un equipo",
            description = "Obtiene la lista completa de miembros de un equipo, incluyendo developers y tech leads, con sus roles y datos de GitHub.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Lista de miembros obtenida exitosamente",
                            content = @Content(schema = @Schema(implementation = TeamMemberResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Equipo no encontrado"
                    )
            }
    )
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembers(
            @Parameter(description = "ID del equipo", required = true)
            @PathVariable Long id) {
        try {
            List<TeamMemberResponse> members = teamManagementService.getTeamMembers(id);
            return ResponseEntity.ok(members);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/members")
    @Operation(
            summary = "Asignar un miembro a un equipo",
            description = "Asigna un usuario (developer o tech lead) a un equipo. El sistema valida automáticamente que: " +
                    "1) El usuario no pertenezca a otro equipo, " +
                    "2) Los tech leads tengan el rol TECH_LEAD. " +
                    "**Restricción:** Un usuario solo puede pertenecer a un equipo a la vez.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Miembro asignado exitosamente al equipo"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Datos de entrada inválidos (usuario no encontrado, rol inválido, etc.)"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Equipo no encontrado"
                    ),
                    @ApiResponse(
                            responseCode = "409",
                            description = "Conflicto: el usuario ya pertenece a otro equipo"
                    )
            }
    )
    public ResponseEntity<Void> assignMember(
            @Parameter(description = "ID del equipo", required = true)
            @PathVariable Long id,
            @Valid @RequestBody AssignMemberRequest request) {
        try {
            teamManagementService.assignMember(id, request.getUserId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(
            summary = "Remover un miembro de un equipo",
            description = "Remueve un usuario (developer o tech lead) de un equipo. El usuario quedará sin equipo asignado después de esta operación.",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "Miembro removido exitosamente del equipo"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "El usuario no pertenece a este equipo o datos inválidos"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Equipo o usuario no encontrado"
                    )
            }
    )
    public ResponseEntity<Void> removeMember(
            @Parameter(description = "ID del equipo", required = true)
            @PathVariable Long id,
            @Parameter(description = "ID del usuario a remover", required = true)
            @PathVariable Long userId) {
        try {
            teamManagementService.removeMember(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/repositories")
    @Operation(
            summary = "Listar repositorios de un equipo",
            description = "Obtiene la lista de repositorios asignados a un equipo. Un equipo puede trabajar en múltiples repositorios y un repositorio puede ser compartido por múltiples equipos.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Lista de repositorios obtenida exitosamente",
                            content = @Content(schema = @Schema(implementation = RepositoryConfig.class))
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Equipo no encontrado"
                    )
            }
    )
    public ResponseEntity<List<RepositoryConfig>> getTeamRepositories(
            @Parameter(description = "ID del equipo", required = true)
            @PathVariable Long id) {
        try {
            List<RepositoryConfig> repositories = teamManagementService.getTeamRepositories(id);
            return ResponseEntity.ok(repositories);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/repositories")
    @Operation(
            summary = "Asignar un repositorio a un equipo",
            description = "Asocia un repositorio con un equipo, permitiendo que el equipo trabaje en ese repositorio. La relación es ManyToMany: un repositorio puede ser asignado a múltiples equipos.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Repositorio asignado exitosamente al equipo"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Datos de entrada inválidos (repositorio no encontrado, etc.)"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Equipo no encontrado"
                    )
            }
    )
    public ResponseEntity<Void> assignRepository(
            @Parameter(description = "ID del equipo", required = true)
            @PathVariable Long id,
            @Valid @RequestBody AssignRepositoryRequest request) {
        try {
            teamManagementService.assignRepository(id, request.getRepositoryConfigId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}/repositories/{repositoryId}")
    @Operation(
            summary = "Remover un repositorio de un equipo",
            description = "Desasocia un repositorio de un equipo. El repositorio seguirá existiendo y puede seguir asignado a otros equipos.",
            responses = {
                    @ApiResponse(
                            responseCode = "204",
                            description = "Repositorio removido exitosamente del equipo"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Datos de entrada inválidos"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Equipo o repositorio no encontrado"
                    )
            }
    )
    public ResponseEntity<Void> removeRepository(
            @Parameter(description = "ID del equipo", required = true)
            @PathVariable Long id,
            @Parameter(description = "ID del repositorio a remover", required = true)
            @PathVariable Long repositoryId) {
        try {
            teamManagementService.removeRepository(id, repositoryId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
