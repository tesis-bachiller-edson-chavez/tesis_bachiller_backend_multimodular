package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_domain.Role;
import org.grubhart.pucp.tesis.module_domain.RoleName;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class) // Importamos la configuración de seguridad para que se aplique al test
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private Oauth2LoginSuccessHandler oauth2LoginSuccessHandler;


    @Test
    @DisplayName("GET /api/v1/user/me debe devolver los datos del usuario autenticado")
    void getCurrentUser_whenAuthenticated_shouldReturnUserData() throws Exception {
        // GIVEN: Un usuario existente en la base de datos
        User user = new User(123L, "test-user", "test@test.com");
        user.getRoles().add(new Role(RoleName.DEVELOPER));
        when(userRepository.findByGithubUsernameIgnoreCase("test-user")).thenReturn(Optional.of(user));

        // WHEN & THEN: Realizamos la petición y verificamos la respuesta
        mockMvc.perform(get("/api/v1/user/me")
                        .with(oauth2Login().attributes(attrs -> {
                            attrs.put("login", "test-user");
                            attrs.put("id", 123L);
                        })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubUsername").value("test-user"))
                .andExpect(jsonPath("$.email").value("test@test.com"))
                .andExpect(jsonPath("$.roles").value(hasItem("DEVELOPER")));
    }

    @Test
    @DisplayName("GET /api/v1/user/me debe devolver 401 si no está autenticado")
    void getCurrentUser_whenNotAuthenticated_shouldReturnUnauthorized() throws Exception {
        // WHEN & THEN: Realizamos la petición sin simular un usuario
        // Spring Security debe interceptarla y devolver 401
        mockMvc.perform(get("/api/v1/user/me"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /api/v1/user/me debe devolver 404 si el usuario autenticado no está en la BD")
   void getCurrentUser_whenAuthenticatedUserNotInDb_shouldReturnNotFound() throws Exception {
        // GIVEN: El usuario está autenticado pero no existe en nuestro repositorio
        when(userRepository.findByGithubUsernameIgnoreCase("ghost-user")).thenReturn(Optional.empty());

        // WHEN & THEN: La petición debe devolver Not Found
        // WHEN & THEN: Realizamos la petición con un usuario simulado que no está en la BD
        mockMvc.perform(get("/api/v1/user/me")
                        .with(oauth2Login().attributes(attrs -> {
                            attrs.put("login", "ghost-user");
                            attrs.put("id", 404L);
                        })))
                .andExpect(status().is3xxRedirection());
    }
}