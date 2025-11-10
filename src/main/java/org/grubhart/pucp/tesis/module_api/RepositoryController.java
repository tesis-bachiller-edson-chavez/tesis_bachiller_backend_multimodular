package org.grubhart.pucp.tesis.module_api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.grubhart.pucp.tesis.module_api.dto.RepositoryDto;
import org.grubhart.pucp.tesis.module_api.dto.RepositorySyncResultDto;
import org.grubhart.pucp.tesis.module_api.dto.UpdateRepositoryRequest;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfigRepository;
import org.grubhart.pucp.tesis.module_domain.RepositorySyncResult;
import org.grubhart.pucp.tesis.module_domain.RepositorySyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador REST para gestionar repositorios de GitHub y su configuración con Datadog.
 */
@RestController
@RequestMapping("/api/v1/repositories")
@Tag(name = "Repositories", description = "API para gestión de repositorios de GitHub y su configuración con Datadog")
@SecurityRequirement(name = "oauth2")
public class RepositoryController {

    private static final Logger logger = LoggerFactory.getLogger(RepositoryController.class);

    private final RepositoryConfigRepository repositoryConfigRepository;
    private final RepositorySyncService repositorySyncService;

    public RepositoryController(
            RepositoryConfigRepository repositoryConfigRepository,
            RepositorySyncService repositorySyncService) {
        this.repositoryConfigRepository = repositoryConfigRepository;
        this.repositorySyncService = repositorySyncService;
    }

    @GetMapping
    @Operation(
            summary = "Listar todos los repositorios",
            description = "Obtiene una lista de todos los repositorios configurados en el sistema, incluyendo su asociación con servicios de Datadog. Disponible para todos los usuarios autenticados.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Lista de repositorios obtenida exitosamente",
                            content = @Content(schema = @Schema(implementation = RepositoryDto.class))
                    )
            }
    )
    public List<RepositoryDto> getAllRepositories() {
        logger.debug("Fetching all repositories");
        return repositoryConfigRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Sincronizar repositorios desde GitHub",
            description = "Sincroniza la lista de repositorios desde GitHub a la base de datos local de forma idempotente. Solo crea nuevos repositorios sin modificar los existentes. **Requiere rol de ADMIN**.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Sincronización completada exitosamente",
                            content = @Content(schema = @Schema(implementation = RepositorySyncResultDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Acceso denegado - requiere rol ADMIN"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Error al sincronizar repositorios desde GitHub"
                    )
            }
    )
    public ResponseEntity<RepositorySyncResultDto> syncRepositories() {
        logger.info("Starting repository synchronization from GitHub");
        try {
            RepositorySyncResult result = repositorySyncService.synchronizeRepositories();
            RepositorySyncResultDto dto = new RepositorySyncResultDto(
                    result.newRepositories(),
                    result.totalRepositories(),
                    result.unchanged()
            );
            logger.info("Repository synchronization completed: {} new, {} unchanged, {} total",
                    result.newRepositories(), result.unchanged(), result.totalRepositories());
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            logger.error("Error during repository synchronization", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Actualizar configuración de un repositorio",
            description = "Actualiza la configuración de un repositorio, específicamente el nombre del servicio de Datadog asociado y el nombre del archivo de workflow de deployment. Ambos campos pueden ser null. **Requiere rol de ADMIN**.",
            parameters = {
                    @Parameter(
                            name = "id",
                            description = "ID del repositorio a actualizar",
                            required = true,
                            example = "1"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Repositorio actualizado exitosamente",
                            content = @Content(schema = @Schema(implementation = RepositoryDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Acceso denegado - requiere rol ADMIN"
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Repositorio no encontrado"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Error al actualizar el repositorio"
                    )
            }
    )
    public ResponseEntity<RepositoryDto> updateRepository(
            @PathVariable Long id,
            @RequestBody UpdateRepositoryRequest request) {
        logger.debug("Updating repository {} with datadogServiceName: {}, deploymentWorkflowFileName: {}",
                id, request.datadogServiceName(), request.deploymentWorkflowFileName());

        try {
            return repositoryConfigRepository.findById(id)
                    .map(repo -> {
                        repo.setDatadogServiceName(request.datadogServiceName());
                        repo.setDeploymentWorkflowFileName(request.deploymentWorkflowFileName());
                        RepositoryConfig updated = repositoryConfigRepository.save(repo);
                        logger.info("Repository {} updated with datadogServiceName: {}, deploymentWorkflowFileName: {}",
                                id, request.datadogServiceName(), request.deploymentWorkflowFileName());
                        return ResponseEntity.ok(mapToDto(updated));
                    })
                    .orElseGet(() -> {
                        logger.warn("Repository not found: {}", id);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            logger.error("Error updating repository {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private RepositoryDto mapToDto(RepositoryConfig repo) {
        return new RepositoryDto(
                repo.getId(),
                repo.getRepositoryUrl(),
                repo.getDatadogServiceName(),
                repo.getOwner(),
                repo.getRepoName(),
                repo.getDeploymentWorkflowFileName()
        );
    }
}