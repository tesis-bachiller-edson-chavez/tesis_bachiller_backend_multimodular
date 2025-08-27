package org.grubhart.pucp.tesis.module_api;


import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.grubhart.pucp.tesis.module_administration.AuthenticationService;
import org.grubhart.pucp.tesis.module_administration.GithubUserDto;
import org.grubhart.pucp.tesis.module_administration.LoginProcessingResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Objects;

@Component
public class Oauth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthenticationService authenticationService;
    private final String frontendUrl;

    public Oauth2LoginSuccessHandler(AuthenticationService authenticationService, @Value("${app.frontend.url}") String frontendUrl) {
        this.authenticationService = authenticationService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        //1 Obtenemos el usuario Oauth2 del objeto de autenticacion de Spring Security
        var oauthUser = (OAuth2User) authentication.getPrincipal();

        //2. Extraemos los atributos que necesitamos. Los nombres ("id", "login", "email")
        // son los que Github proporciona por defecto

        // Obtenemos el ID como Number para evitar ClassCastException y lo convertimos a Long.
        // Esto funciona sin importar si la API devuelve un Integer o un Long.
        Number idNumber = oauthUser.getAttribute("id");
        if (idNumber == null) {
            throw new ServletException("El proveedor OAuth2 no devolvió un ID de usuario, no se puede continuar con el login.");
        }
        Long id = idNumber.longValue();
        String username = oauthUser.getAttribute("login");
        if(username == null || username.isBlank()) {
            throw new ServletException("El proveedor OAuth2 no devolvió un nombre de usuario (login), no se puede continuar con el login.");
        }
        String email = Objects.requireNonNullElse(oauthUser.getAttribute("email"), "no-email@placeholder.com");


        // 3. creamos nuestro DTO y Llamamos a nuestro servicio de Negocio.
        var githubUserDto = new GithubUserDto(id,username,email);
        LoginProcessingResult result = authenticationService.processNewLogin(githubUserDto);

        //4. Redirigimos al usuario a la pagina principal de la aplicacion.
        if (result.isFirstAdmin()) {
            // AC 3.2: Redirigir al primer administrador a la página de configuración.
            response.sendRedirect("/admin/setup");
        } else {
            // AC 1.2: Redirigir a los usuarios normales a un dashboard principal.
            response.sendRedirect(frontendUrl + "/home");
        }

    }

}
