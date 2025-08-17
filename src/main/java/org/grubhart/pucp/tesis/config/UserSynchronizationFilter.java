package org.grubhart.pucp.tesis.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.grubhart.pucp.tesis.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class UserSynchronizationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(UserSynchronizationFilter.class);
    private final UserRepository userRepository;

    public UserSynchronizationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        // Solo actuamos si el usuario está autenticado y es un usuario de OAuth2
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof OAuth2User) {
            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
            String username = oauthUser.getAttribute("login");

            // Si el username es válido, verificamos si existe en nuestra BD
            if (username != null && userRepository.findByGithubUsernameIgnoreCase(username).isEmpty()) { // .isEmpty() es más legible desde Java 11+
                logger.warn("Usuario '{}' autenticado en la sesión pero no encontrado en la base de datos. " +
                        "Esto puede ocurrir después de un reinicio con una BD en memoria o por perdida de datos en la tabla USERS y USERS_ROLE. Invalidando sesión.", username);

                // El usuario no existe en la BD, lo que indica una sesión desincronizada.
                // Invalidamos la sesión para forzar un nuevo login.
                request.getSession().invalidate();
                SecurityContextHolder.clearContext();

                // IMPORTANTE: Detenemos la cadena de filtros aquí y forzamos la redirección.
                // Esto evita que la petición continúe hacia el controlador con un contexto de seguridad nulo.
                response.sendRedirect("/oauth2/authorization/github");
                return;
            }
        }

        // Continuamos con el resto de la cadena de filtros
        filterChain.doFilter(request, response);
    }
}