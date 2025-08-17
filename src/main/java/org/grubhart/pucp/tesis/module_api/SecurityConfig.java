package org.grubhart.pucp.tesis.module_api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final Oauth2LoginSuccessHandler oauth2LoginSuccessHandler;
    private final UserSynchronizationFilter userSynchronizationFilter;

    public SecurityConfig(Oauth2LoginSuccessHandler oauth2LoginSuccessHandler, UserSynchronizationFilter userSynchronizationFilter) {
        this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
        this.userSynchronizationFilter = userSynchronizationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Nos aseguramos de que nuestro filtro se ejecute ANTES que el AnonymousAuthenticationFilter.
                // Esto es crucial para que podamos probar el caso en que el objeto de autenticación es
                // verdaderamente nulo, antes de que Spring lo pueble con un token anónimo.
                .addFilterBefore(userSynchronizationFilter, AnonymousAuthenticationFilter.class)
                // Deshabilitamos CSRF para la consola H2, que no lo necesita.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/error", "/h2-console/**").permitAll() // Permite acceso público a la consola H2 Home y pagina de error
                        .anyRequest().authenticated() // Requiere autenticación para cualquier otra petición
                )
                .oauth2Login(oauth2 ->
                        oauth2.successHandler(oauth2LoginSuccessHandler))
                // Permitimos que la consola H2 se muestre en un frame.
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }
}