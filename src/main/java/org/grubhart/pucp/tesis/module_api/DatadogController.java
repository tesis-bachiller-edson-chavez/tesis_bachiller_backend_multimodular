package org.grubhart.pucp.tesis.module_api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.grubhart.pucp.tesis.module_api.dto.DatadogServiceDto;
import org.grubhart.pucp.tesis.module_domain.DatadogServiceCollector;
import org.grubhart.pucp.tesis.module_domain.DatadogServicesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for Datadog-related operations.
 * Provides endpoints to fetch information from Datadog API without exposing credentials to frontend.
 */
@RestController
@RequestMapping("/api/v1/datadog")
@Tag(name = "Datadog", description = "API para obtener información de Datadog")
@SecurityRequirement(name = "oauth2")
public class DatadogController {

    private static final Logger logger = LoggerFactory.getLogger(DatadogController.class);
    private final DatadogServiceCollector datadogServiceCollector;

    public DatadogController(DatadogServiceCollector datadogServiceCollector) {
        this.datadogServiceCollector = datadogServiceCollector;
    }

    @GetMapping("/services")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Obtener lista de servicios de Datadog",
            description = "Retorna la lista de servicios disponibles en Datadog APM. " +
                    "Esta información se utiliza para configurar el campo `datadogServiceName` en los repositorios. " +
                    "**Requiere rol de ADMIN**.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Lista de servicios obtenida exitosamente",
                            content = @Content(schema = @Schema(implementation = DatadogServiceDto.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Acceso denegado - requiere rol ADMIN"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Error al consultar la API de Datadog"
                    )
            }
    )
    public ResponseEntity<List<DatadogServiceDto>> getServices() {
        logger.info("Fetching Datadog services list");

        try {
            DatadogServicesResponse response = datadogServiceCollector.getServices();

            if (response == null || response.data() == null) {
                logger.warn("Received null or empty response from Datadog services API");
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<DatadogServiceDto> services = response.data().stream()
                    .filter(serviceData -> serviceData.attributes() != null)
                    .filter(serviceData -> serviceData.attributes().serviceName() != null)
                    .map(serviceData -> new DatadogServiceDto(serviceData.attributes().serviceName()))
                    .distinct()
                    .sorted((s1, s2) -> s1.serviceName().compareToIgnoreCase(s2.serviceName()))
                    .collect(Collectors.toList());

            logger.info("Successfully fetched {} Datadog services", services.size());
            return ResponseEntity.ok(services);

        } catch (Exception e) {
            logger.error("Error fetching Datadog services", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
