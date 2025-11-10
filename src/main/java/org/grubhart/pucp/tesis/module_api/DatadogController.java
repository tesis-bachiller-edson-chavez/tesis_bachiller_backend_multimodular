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
 * Controlador REST para interactuar con la API de Datadog.
 */
@RestController
@RequestMapping("/api/v1/datadog")
@Tag(name = "Datadog", description = "API para interactuar con servicios de Datadog")
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
            summary = "Obtener servicios de Datadog APM",
            description = "Obtiene la lista de servicios con trazas APM activas en el environment configurado. " +
                    "Este endpoint consulta el API de Service Dependencies de Datadog, que automáticamente " +
                    "incluye todos los servicios que tienen instrumentación APM y están enviando trazas. " +
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
                            description = "Error al obtener servicios de Datadog"
                    )
            }
    )
    public ResponseEntity<List<DatadogServiceDto>> getServices() {
        logger.debug("Fetching services from Datadog APM");

        try {
            DatadogServicesResponse response = datadogServiceCollector.getServices();

            if (response == null || response.data() == null) {
                logger.warn("No services found in Datadog APM response");
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<DatadogServiceDto> services = response.data().stream()
                    .filter(service -> service.attributes() != null && service.attributes().name() != null)
                    .map(service -> new DatadogServiceDto(service.attributes().name()))
                    .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                    .collect(Collectors.toList());

            logger.info("Successfully fetched {} services from Datadog APM", services.size());
            return ResponseEntity.ok(services);
        } catch (Exception e) {
            logger.error("Error fetching services from Datadog APM", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
