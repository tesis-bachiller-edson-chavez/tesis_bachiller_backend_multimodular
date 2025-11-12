package org.grubhart.pucp.tesis.module_api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.grubhart.pucp.tesis.module_processor.DeveloperDashboardService;
import org.grubhart.pucp.tesis.module_processor.DeveloperMetricsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Controller
public class DashboardController {

    @GetMapping("/dashboard")
    public ResponseEntity<String> showDashboard(Authentication authentication) {
        String username = "Usuario";
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User) {
            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
            username = oauthUser.getAttribute("login");
        }

        String htmlContent = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <title>DORA Dashboard</title>
                <style>
                    body { font-family: sans-serif; display: flex; justify-content: center; align-items: center; height: 100vh; background-color: #f4f4f9; margin: 0; }
                    .container { text-align: center; padding: 40px; background-color: white; border-radius: 8px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); }
                    h1 { color: #333; }
                    p { color: #666; }
                    a { color: #007bff; text-decoration: none; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>¡Bienvenido, %s!</h1>
                    <p>Este es el dashboard principal de la aplicación.</p>
                    <p>El frontend completo y la integración con Grafana se implementarán en un sprint futuro.</p>
                    <p><a href="/logout">Cerrar Sesión</a></p>
                </div>
            </body>
            </html>
            """.formatted(username);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlContent);
    }

    @GetMapping("/admin/setup")
    public ResponseEntity<String> showAdminSetup() {
        String htmlContent = """
            <!DOCTYPE html>
            <html lang="es"><head><meta charset="UTF-8"><title>Configuración Inicial</title></head>
            <body><h1>Página de Configuración del Administrador</h1><p>Aquí se configurará el sistema.</p></body></html>
            """;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(htmlContent);
    }
}

/**
 * Controlador REST para endpoints de API del dashboard.
 * Endpoints separados por nivel de acceso (Developer, Manager, etc.)
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "API para métricas del dashboard según rol del usuario")
@SecurityRequirement(name = "oauth2")
class DashboardApiController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardApiController.class);

    private final DeveloperDashboardService developerDashboardService;

    public DashboardApiController(DeveloperDashboardService developerDashboardService) {
        this.developerDashboardService = developerDashboardService;
    }

    @GetMapping("/developer/metrics")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'TECH_LEAD', 'ENGINEERING_MANAGER', 'ADMIN')")
    @Operation(
            summary = "Obtener métricas del dashboard para Developer",
            description = "Retorna métricas personalizadas para el rol Developer. " +
                    "Los datos incluyen únicamente repositorios donde el developer ha realizado commits, " +
                    "estadísticas de commits y pull requests. " +
                    "**Filtros opcionales:** startDate, endDate (basados en fecha de deployment), repositoryIds. " +
                    "**Accesible para usuarios con rol DEVELOPER o superior**.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Métricas obtenidas exitosamente",
                            content = @Content(schema = @Schema(implementation = DeveloperMetricsResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Acceso denegado - requiere rol DEVELOPER o superior"
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Error interno al calcular métricas"
                    )
            }
    )
    public ResponseEntity<DeveloperMetricsResponse> getDeveloperMetrics(
            Authentication authentication,
            @Parameter(description = "Fecha de inicio del rango (formato: YYYY-MM-DD, basado en fecha de deployment)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Fecha de fin del rango (formato: YYYY-MM-DD, basado en fecha de deployment)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Lista de IDs de repositorios para filtrar")
            @RequestParam(required = false) List<Long> repositoryIds) {
        try {
            if (!(authentication.getPrincipal() instanceof OAuth2User)) {
                logger.error("El principal de la autenticación no es de tipo OAuth2User");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
            String githubUsername = oauthUser.getAttribute("login");

            if (githubUsername == null || githubUsername.isBlank()) {
                logger.error("No se pudo obtener el nombre de usuario de GitHub del usuario autenticado");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            logger.info("Solicitando métricas de developer para el usuario: {} (startDate: {}, endDate: {}, repositoryIds: {})",
                    githubUsername, startDate, endDate, repositoryIds);

            DeveloperMetricsResponse metrics = developerDashboardService.getDeveloperMetrics(
                    githubUsername, startDate, endDate, repositoryIds);
            return ResponseEntity.ok(metrics);

        } catch (Exception e) {
            logger.error("Error al obtener métricas del developer", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}