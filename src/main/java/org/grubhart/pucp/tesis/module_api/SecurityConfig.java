package org.grubhart.pucp.tesis.module_api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final UserSynchronizationFilter userSynchronizationFilter;
    private final Oauth2LoginSuccessHandler oauth2LoginSuccessHandler;

    public SecurityConfig(UserSynchronizationFilter userSynchronizationFilter,
                          Oauth2LoginSuccessHandler oauth2LoginSuccessHandler) {
        this.userSynchronizationFilter = userSynchronizationFilter;
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .addFilterBefore(userSynchronizationFilter, AnonymousAuthenticationFilter.class)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/logout", "/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/error",
                                "/h2-console/**",
                                "/actuator/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(e -> e
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oauth2LoginSuccessHandler)
                )
                .logout(logout -> logout
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication != null && authentication.getName() != null) {
                                log.info("LOGOUT_SUCCESS: Sesión invalidada para el usuario '''{}}''''.", authentication.getName());
                            } else {
                                log.info("LOGOUT_SUCCESS: Sesión invalidada para un usuario anónimo.");
                            }
                            response.setStatus(HttpStatus.OK.value());
                        })
                )
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        serializer.setSameSite("None");
        serializer.setUseSecureCookie(true);
        return serializer;
    }
}
