package org.grubhart.pucp.tesis.module_api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

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
                var userOptional = userRepository.findByGithubUsernameIgnoreCase(username);

                if (userOptional.isEmpty()) {
                    logger.warn("Usuario '{}' autenticado en la sesión pero no encontrado en la base de datos. " +
                            "Esto puede ocurrir después de un reinicio con una BD en memoria. Invalidando sesión.", username);

                    request.getSession().invalidate();
                    SecurityContextHolder.clearContext();

                    response.sendRedirect("/oauth2/authorization/github");
                    return;
                } else {
                    User user = userOptional.get();

                    // Para evitar logs en cada petición, solo lo mostramos una vez por sesión.
                    Object loginLoggedFlag = request.getSession().getAttribute("LOGIN_LOGGED");
                    if (loginLoggedFlag == null) {
                        logger.info("LOGIN_SUCCESS: Nueva sesión verificada para el usuario '{}'.", username);
                        request.getSession().setAttribute("LOGIN_LOGGED", true);
                    }

                    // Verificar si necesitamos actualizar el Authentication con los roles de la BD
                    Object rolesUpdatedFlag = request.getSession().getAttribute("ROLES_UPDATED");
                    if (rolesUpdatedFlag == null && authentication instanceof OAuth2AuthenticationToken) {
                        // Convertir los roles de la BD a GrantedAuthorities
                        Set<GrantedAuthority> authorities = user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                                .collect(Collectors.toSet());

                        logger.debug("Actualizando authorities para el usuario '{}' con roles: {}",
                                username, authorities);

                        // Crear un nuevo OAuth2User con las mismas propiedades pero con las nuevas authorities
                        OAuth2User newOAuth2User = new DefaultOAuth2User(
                                authorities,
                                oauthUser.getAttributes(),
                                "login" // El atributo que se usa como nombre (username)
                        );

                        // Crear un nuevo OAuth2AuthenticationToken con las authorities actualizadas
                        OAuth2AuthenticationToken newAuth = new OAuth2AuthenticationToken(
                                newOAuth2User,
                                authorities,
                                ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId()
                        );

                        // Actualizar el SecurityContext con el nuevo Authentication
                        SecurityContextHolder.getContext().setAuthentication(newAuth);

                        // Marcar que ya actualizamos los roles en esta sesión
                        request.getSession().setAttribute("ROLES_UPDATED", true);

                        logger.info("Authorities actualizadas para el usuario '{}': {}", username, authorities);
                    }
                }
            }
        }

        // Continuamos con el resto de la cadena de filtros
        filterChain.doFilter(request, response);
    }
}