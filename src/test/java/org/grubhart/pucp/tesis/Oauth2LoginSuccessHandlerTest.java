package org.grubhart.pucp.tesis;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.grubhart.pucp.tesis.administration.AuthenticationService;
import org.grubhart.pucp.tesis.administration.GithubUserDto;
import org.grubhart.pucp.tesis.config.Oauth2LoginSuccessHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.IOException;
import jakarta.servlet.ServletException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class Oauth2LoginSuccessHandlerTest {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private Oauth2LoginSuccessHandler successHandler;

    @BeforeEach
    public void setUp() {
        // No es estrictamente necesario para este test, pero es buena práctica
        successHandler = new Oauth2LoginSuccessHandler(authenticationService);
    }

    @Test
    @DisplayName("Al loguearse exitosamente, debe procesar el usuario y redirigir a la raiz")
    void onAuthenticationSuccess_shouldProcessUserAndRedirect() throws IOException, ServletException {
        // GIVEN
        // 1. Creamos un usuario OAuth2 simulado, como el que nos daría GitHub
        Map<String, Object> userAttributesWithEmail = Map.of(
                "id", 12345,
                "login", "github-user",
                "email", "user@github.com"
        );
        OAuth2User oAuth2User = new DefaultOAuth2User(Collections.emptyList(), userAttributesWithEmail, "id");
        Authentication authentication = new OAuth2AuthenticationToken(oAuth2User, Collections.emptyList(), "github");
        // WHEN
        successHandler.onAuthenticationSuccess(request, response, authentication);
        // THEN
        // 1. Verificamos que se llamó a nuestro servicio de autenticación
        var dtoCaptor = ArgumentCaptor.forClass(GithubUserDto.class);
        verify(authenticationService).processNewLogin(dtoCaptor.capture());

        // 2. Verificamos que los datos pasados al servicio son correctos
        var captureDto = dtoCaptor.getValue();
        assertThat(captureDto.id()).isEqualTo(12345L);
        assertThat(captureDto.username()).isEqualTo("github-user");
        assertThat(captureDto.email()).isEqualTo("user@github.com");

        // 3. Verificamos que se redirige al usuario a la página principal
        verify(response).sendRedirect("/api/v1/user/me");
    }


    @Test
    @DisplayName("Cuando el email del usuario es nulo, debe usar un email de respaldo")
    void onAuthenticationSuccess_whenEmailIsNull_shouldUsePlaceholder() throws IOException, ServletException {
        // GIVEN
        // 1. Creamos un usuario OAuth2 simulado con el email nulo
        // Map.of() no permite valores nulos, por lo que usamos un HashMap para este caso de prueba.
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("id", 54321);
        attributes.put("login", "private-email-user");
        attributes.put("email", null);

        OAuth2User oAuth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "id");
        Authentication authentication = new OAuth2AuthenticationToken(oAuth2User, Collections.emptyList(), "github");

        // WHEN
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // THEN
        // 1. Verificamos que se llamó al servicio
        var dtoCaptor = ArgumentCaptor.forClass(GithubUserDto.class);
        verify(authenticationService).processNewLogin(dtoCaptor.capture());

        // 2. Verificamos que el DTO capturado contiene el email de respaldo
        var capturedDto = dtoCaptor.getValue();
        assertThat(capturedDto.email()).isEqualTo("no-email@placeholder.com");
    }

    @Test
    @DisplayName("Cuando el ID del usuario es nulo, debe lanzar una excepción")
    void onAuthenticationSuccess_whenIdIsNull_shouldThrowException() {
        // GIVEN
        // 1. Creamos un usuario OAuth2 simulado con el ID nulo
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("id", null);
        attributes.put("login", "no-id-user");
        attributes.put("email", "no-id@github.com");

        OAuth2User oAuth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "login");
        Authentication authentication = new OAuth2AuthenticationToken(oAuth2User, Collections.emptyList(), "github");

        // WHEN & THEN
        // Verificamos que se lanza la excepción esperada y que el mensaje es informativo.
        ServletException exception = assertThrows(ServletException.class, () -> {
            successHandler.onAuthenticationSuccess(request, response, authentication);
        });
        assertThat(exception.getMessage()).contains("no devolvió un ID de usuario");
    }

    @Test
    @DisplayName("Cuando el username del usuario es nulo, debe lanzar una excepción")
    void onAuthenticationSuccess_whenUsernameIsNull_shouldThrowException() {
        // GIVEN
        // 1. Creamos un usuario OAuth2 simulado con el username nulo
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("id", 12345L);
        attributes.put("login", null);
        attributes.put("email", "no-id@github.com");

        OAuth2User oAuth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "id");
        Authentication authentication = new OAuth2AuthenticationToken(oAuth2User, Collections.emptyList(), "github");

        // WHEN & THEN
        // Verificamos que se lanza la excepción esperada y que el mensaje es informativo.
        ServletException exception = assertThrows(ServletException.class, () -> {
            successHandler.onAuthenticationSuccess(request, response, authentication);
        });
        assertThat(exception.getMessage()).contains("no devolvió un nombre de usuario");
    }


    @Test
    @DisplayName("Cuando el username del usuario esta en blanco, debe lanzar una excepción")
    void onAuthenticationSuccess_whenUsernameIsBlank_shouldThrowException() {
        // GIVEN
        // 1. Creamos un usuario OAuth2 simulado con el username en blanco
        Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("id", 12345L);
        attributes.put("login", "   ");
        attributes.put("email", "no-id@github.com");

        OAuth2User oAuth2User = new DefaultOAuth2User(Collections.emptyList(), attributes, "id");
        Authentication authentication = new OAuth2AuthenticationToken(oAuth2User, Collections.emptyList(), "github");

        //WHEN & THEN
        //Verificamos que se lanza la excepcion esperada y que el mensaje es informativo.
        ServletException exception = assertThrows(ServletException.class, () -> {
            successHandler.onAuthenticationSuccess(request, response, authentication);
        });
        assertThat(exception.getMessage()).contains("no devolvió un nombre de usuario");
    }
}