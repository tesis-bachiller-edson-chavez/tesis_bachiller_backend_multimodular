package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_administration.AuthenticationService;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserSynchronizationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthenticationService authenticationService; // Requerido por Oauth2LoginSuccessHandler

    @MockitoSpyBean
    private UserSynchronizationFilter userSynchronizationFilter;

    @Test
    @DisplayName("Filtro: Dado un usuario autenticado que SÍ existe en la BD, la sesión NO se invalida")
    void whenUserExistsInDb_sessionIsNotInvalidated() throws Exception {
        // Arrange: Simulamos que el usuario "testuser" existe en la base de datos.
        User mockUser = new User(123L, "testuser", "test@user.com");
        // Definimos explícitamente el comportamiento para llamadas consecutivas.
        // 1. La primera llamada es en UserSynchronizationFilter.
        // 2. La segunda llamada es en UserController.
        when(userRepository.findByGithubUsernameIgnoreCase("testuser"))
                .thenReturn(Optional.of(mockUser)) // Para la primera llamada
                .thenReturn(Optional.of(mockUser)); // Para la segunda llamada

        // Act & Assert: Realizamos una petición a un endpoint protegido.
        // Usamos oauth2Login() para simular un usuario autenticado en la sesión.
        // Esperamos una respuesta 200 OK, lo que significa que el filtro no interrumpió el flujo
        // y el UserController pudo procesar la petición.
        mockMvc.perform(get("/api/v1/user/me").with(oauth2Login().attributes(attrs -> {
                    attrs.put("login", "testuser");
                    attrs.put("id", 123L);
                })))
                .andExpect(status().isOk());

        // Verificamos que la búsqueda en el repositorio se hizo (una vez en el filtro, otra en el controller).
        verify(userRepository, times(2)).findByGithubUsernameIgnoreCase("testuser");
    }

    @Test
    @DisplayName("Filtro: Dado un usuario autenticado que NO existe en la BD, la sesión se invalida y se redirige al login")
    void whenUserNotInDb_sessionIsInvalidatedAndRedirectsToLogin() throws Exception {
        // Arrange: Simulamos que el usuario "ghostuser" NO existe en la base de datos.
        when(userRepository.findByGithubUsernameIgnoreCase("ghostuser")).thenReturn(Optional.empty());

        // Act & Assert: Realizamos una petición a un endpoint protegido.
        // Esperamos una redirección (302), porque el filtro debe invalidar la sesión.
        // Al estar la sesión invalidada, Spring Security trata al usuario como anónimo
        // e inicia el flujo de login de OAuth2, redirigiendo a GitHub.
        mockMvc.perform(get("/api/v1/user/me")
                .with(oauth2Login().attributes(attrs -> {
                    attrs.put("login", "ghostuser"); // Corregido para coincidir con el mock
                    attrs.put("id", 404L);
                })))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/github"));

        // Verificamos que la búsqueda en el repositorio se hizo (solo en el filtro).
        verify(userRepository, times(1)).findByGithubUsernameIgnoreCase("ghostuser");
    }

    @Test
    @DisplayName("Filtro: Dada una petición anónima, el filtro no realiza ninguna acción de sincronización")
    void whenRequestIsAnonymous_filterDoesNothing() throws Exception {
        // Act & Assert: Realizamos una petición anónima a un endpoint protegido.
        // Esperamos la redirección normal al login, sin que nuestro filtro haga nada especial.
        mockMvc.perform(get("/api/v1/user/me"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/oauth2/authorization/github"));

        // Verificamos que el filtro ni siquiera intentó buscar un usuario en la BD.
        verify(userRepository, never()).findByGithubUsernameIgnoreCase(anyString());
    }

    @Test
    @DisplayName("Filtro: Dado un principal que no es OAuth2User, la petición falla en el controlador")
    void whenPrincipalIsNotOAuth2User_requestFailsInController() throws Exception {
        // WHEN & THEN: La petición fallará en el controlador con una ClassCastException,
        // porque el filtro (correctamente) no hace nada y deja pasar la petición.
        // Esperamos un 500 Internal Server Error.
        mockMvc.perform(get("/api/v1/user/me").with(user("standard-user")))
                .andExpect(status().isInternalServerError());

        // Verificamos que nuestro filtro no interactuó con el repositorio.
        verify(userRepository, never()).findByGithubUsernameIgnoreCase(anyString());
    }

    @Test
    @DisplayName("Filtro: Dado un OAuth2User sin atributo 'login', el filtro no actúa y el controlador devuelve 404")
    void whenOAuth2UserHasNoLoginAttribute_filterDoesNothingAndControllerReturnsNotFound() throws Exception {
        // Arrange: Simulamos que el usuario está autenticado vía OAuth2 pero no tiene el atributo 'login'.
        // El filtro no debería hacer nada, y la petición llegará al controlador.
        // El controlador intentará buscar un usuario con username 'null' y no lo encontrará.
        when(userRepository.findByGithubUsernameIgnoreCase(null)).thenReturn(Optional.empty());

        // Act & Assert: Realizamos la petición al endpoint protegido.
        // Esperamos un 404 Not Found, que es la respuesta del controlador cuando no encuentra al usuario.
        mockMvc.perform(get("/api/v1/user/me")
                        .with(oauth2Login().attributes(attrs -> {
                            // No incluimos el atributo 'login'
                            attrs.put("id", 12345L);
                            attrs.put("email", "no-login@test.com");
                        })))
                .andExpect(status().isNotFound());

        // Verificamos que el filtro no hizo nada (porque el username es null),
        // pero el controlador sí interactuó con el repositorio.
        verify(userRepository, times(1)).findByGithubUsernameIgnoreCase(null);
    }
    
    @Test
    @DisplayName("Filtro: Dado un token de autenticación NO autenticado, el filtro se ejecuta pero no actúa")
    void whenAuthenticationIsNotAuthenticated_filterDoesNothing() throws Exception {
        // Arrange: Creamos un token que, aunque contiene un principal, está explícitamente
        // marcado como NO autenticado.
        OAuth2User mockUser = new DefaultOAuth2User(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                Map.of("login", "unauthenticated-user"),
                "login"
        );
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(
                mockUser,
                Collections.emptyList(),
                "github"
        );
        token.setAuthenticated(false); // ¡Esta es la clave para la prueba!

        // Act & Assert: Realizamos la petición.
        // En un contexto de integración completo, la petición a una URL pública (`/`)
        // debe pasar por nuestro filtro. El filtro no actuará (porque isAuthenticated es falso).
        // La cadena continuará, la autorización para `/` pasará (permitAll), y finalmente
        // se obtendrá un 404 al no encontrar un controlador para esa ruta.
        mockMvc.perform(get("/").with(authentication(token)))
                .andExpect(status().isNotFound());

        // Verify: Verificamos que el método principal del filtro fue invocado (demostrando que se ejecutó)
        verify(userSynchronizationFilter, times(1)).doFilterInternal(any(), any(), any());
        // Y verificamos que, debido a que el token no estaba autenticado, no se intentó sincronizar al usuario.
        verify(userRepository, never()).findByGithubUsernameIgnoreCase(anyString());
    }

    @Test
    @DisplayName("Filtro: Dado un contexto de seguridad sin autenticación, el filtro no actúa")
    void whenSecurityContextHasNoAuthentication_filterDoesNothing() throws Exception {
            // Arrange: Creamos un contexto de seguridad explícitamente vacío.
            // Esto simula un estado anómalo o un punto muy temprano en la cadena de filtros
            // donde la autenticación aún no ha sido establecida.
            // Esto cubrirá la rama `authentication == null`.
            SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();

            // Act & Assert: Realizamos una petición a una URL pública con el contexto vacío.
            // Esperamos un 404, ya que el filtro no debe hacer nada y la petición continuará
            // hasta no encontrar un controlador para la raíz.
            mockMvc.perform(get("/")
                            .with(securityContext(emptyContext)))
                    .andExpect(status().isNotFound());

            // Verify: Verificamos que el filtro se ejecutó pero no interactuó con la BD.
            verify(userSynchronizationFilter, times(1)).doFilterInternal(any(), any(), any());
            verify(userRepository, never()).findByGithubUsernameIgnoreCase(anyString());
    }

    @Test
    @DisplayName("Filtro: Dado un OAuth2User con 'login' en blanco, la sesión se invalida y se redirige")
        void whenOAuth2UserHasBlankLoginAttribute_sessionIsInvalidatedAndRedirects() throws Exception {
        // Arrange: Simulamos que el atributo 'login' es una cadena de espacios en blanco.
        // El filtro intentará buscar un usuario con este nombre, no lo encontrará,
        // e invalidará la sesión, forzando una redirección al login.
        String blankUsername = "   ";
        when(userRepository.findByGithubUsernameIgnoreCase(blankUsername)).thenReturn(Optional.empty());

        // Act & Assert: Realizamos la petición.
        mockMvc.perform(get("/api/v1/user/me")
                        .with(oauth2Login().attributes(attrs -> {
                            attrs.put("login", blankUsername);
                            attrs.put("id", 54321L);
                        })))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/github"));

        // Verify: El filtro busca al usuario con el nombre en blanco, no lo encuentra,
        // invalida la sesión y redirige.
        verify(userRepository, times(1)).findByGithubUsernameIgnoreCase(blankUsername);

    }






}