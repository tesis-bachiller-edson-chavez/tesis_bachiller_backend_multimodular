package org.grubhart.pucp.tesis.module_api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
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

            if (username != null) {
                if (userRepository.findByGithubUsernameIgnoreCase(username).isEmpty()) {
                    logger.warn("Usuario '{}' autenticado en la sesión pero no encontrado en la base de datos. " +
                            "Esto puede ocurrir después de un reinicio con una BD en memoria. Invalidando sesión.", username);

                    request.getSession().invalidate();
                    SecurityContextHolder.clearContext();

                    response.sendRedirect("/oauth2/authorization/github");
                    return;
                } else {
                    // El usuario existe en la BD, la sesión es válida.
                    // Para evitar logs en cada petición, solo lo mostramos una vez por sesión.
                    Object loginLoggedFlag = request.getSession().getAttribute("LOGIN_LOGGED");
                    if (loginLoggedFlag == null) {
                        logger.info("LOGIN_SUCCESS: Nueva sesión verificada para el usuario '{}'.", username);
                        request.getSession().setAttribute("LOGIN_LOGGED", true);
                    }
                }
            }
        }

        // Continuamos con el resto de la cadena de filtros
        filterChain.doFilter(request, response);
    }
}