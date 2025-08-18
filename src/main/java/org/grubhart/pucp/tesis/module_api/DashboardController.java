package org.grubhart.pucp.tesis.module_api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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