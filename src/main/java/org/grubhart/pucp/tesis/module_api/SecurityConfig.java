package org.grubhart.pucp.tesis.module_api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;


import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final Oauth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final UserSynchronizationFilter userSynchronizationFilter;

    public SecurityConfig(Oauth2LoginSuccessHandler oauth2LoginSuccessHandler, UserSynchronizationFilter userSynchronizationFilter) {
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.userSynchronizationFilter = userSynchronizationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterBefore(userSynchronizationFilter, AnonymousAuthenticationFilter.class)
                // Deshabilitamos CSRF para la consola H2, logout y nuestra API
                .csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/h2-console/**"), new AntPathRequestMatcher("/logout"), new AntPathRequestMatcher("/api/**")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new AntPathRequestMatcher("/"), new AntPathRequestMatcher("/error"), new AntPathRequestMatcher("/h2-console/**")).permitAll() // Permite acceso público a la consola H2 Home y pagina de error
                        .anyRequest().authenticated() // Requiere autenticación para cualquier otra petición
                )
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                .oauth2Login(oauth2 ->
                        oauth2.successHandler(oauth2LoginSuccessHandler)
                )
                .logout(logout -> logout
                        .logoutSuccessHandler(new LogoutSuccessHandler() {
                            @Override
                            public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
                                if (authentication != null && authentication.getName() != null) {
                                    log.info("LOGOUT_SUCCESS: Sesión invalidada para el usuario '{}'.", authentication.getName());
                                } else {
                                    log.info("LOGOUT_SUCCESS: Sesión invalidada para un usuario anónimo.");
                                }
                                response.setStatus(HttpStatus.OK.value());
                            }
                        })
                )
                // Permitimos que la consola H2 se muestre en un frame.
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }
}
