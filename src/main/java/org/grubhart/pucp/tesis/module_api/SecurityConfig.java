package org.grubhart.pucp.tesis.module_api;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.grubhart.pucp.tesis.module_administration.AuthenticationService;
import org.grubhart.pucp.tesis.module_administration.GithubUserDto;
import org.grubhart.pucp.tesis.module_administration.LoginProcessingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.io.IOException;
import java.util.Objects;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final UserSynchronizationFilter userSynchronizationFilter;
    private final AuthenticationService authenticationService;
    private final String frontendUrl;

    public SecurityConfig(UserSynchronizationFilter userSynchronizationFilter,
                          AuthenticationService authenticationService,
                          @Value("${app.frontend.url}") String frontendUrl) {
        this.userSynchronizationFilter = userSynchronizationFilter;
        this.authenticationService = authenticationService;
        this.frontendUrl = frontendUrl;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterBefore(userSynchronizationFilter, AnonymousAuthenticationFilter.class)
                .csrf(csrf -> csrf.ignoringRequestMatchers(new AntPathRequestMatcher("/h2-console/**"), new AntPathRequestMatcher("/logout"), new AntPathRequestMatcher("/api/**")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new AntPathRequestMatcher("/"), new AntPathRequestMatcher("/error"), new AntPathRequestMatcher("/h2-console/**")).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler((request, response, authentication) -> {
                            var oauthUser = (OAuth2User) authentication.getPrincipal();
                            Number idNumber = oauthUser.getAttribute("id");
                            if (idNumber == null) {
                                throw new ServletException("El proveedor OAuth2 no devolvió un ID de usuario.");
                            }
                            Long id = idNumber.longValue();
                            String username = oauthUser.getAttribute("login");
                            if (username == null || username.isBlank()) {
                                throw new ServletException("El proveedor OAuth2 no devolvió un nombre de usuario (login).");
                            }
                            String email = Objects.requireNonNullElse(oauthUser.getAttribute("email"), "no-email@placeholder.com");

                            var githubUserDto = new GithubUserDto(id, username, email);
                            LoginProcessingResult result = authenticationService.processNewLogin(githubUserDto);

                            if (result.isFirstAdmin()) {
                                response.sendRedirect("/admin/setup");
                            } else {
                                response.sendRedirect(frontendUrl + "/home");
                            }
                        })
                )
                .logout(logout -> logout
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null && authentication.getName() != null) {
                                log.info("LOGOUT_SUCCESS: Sesión invalidada para el usuario '{}'.", authentication.getName());
                            } else {
                                log.info("LOGOUT_SUCCESS: Sesión invalidada para un usuario anónimo.");
                            }
                            response.setStatus(HttpStatus.OK.value());
                        })
                )
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }
}
