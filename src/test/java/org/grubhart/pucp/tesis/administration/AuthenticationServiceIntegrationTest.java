package org.grubhart.pucp.tesis.administration;

import org.grubhart.pucp.tesis.domain.RoleName;
import org.grubhart.pucp.tesis.domain.RoleRepository;
import org.grubhart.pucp.tesis.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = {"dora.initial-admin-username=admin-user"})
class AuthenticationServiceIntegrationTest {

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;
    private final String INITIAL_ADMIN_USERNAME = "admin-user";


    @Test
    @DisplayName("Al procesar múltiples logins (admin y developer), no debe haber conflictos de persistencia de roles")
    void processNewLogin_withMultipleRoles_shouldNotCausePersistenceConflict() {
        // GIVEN: Una base de datos limpia (garantizada por @DirtiesContext)
        var adminDto = new GithubUserDto(1L, INITIAL_ADMIN_USERNAME, "admin@test.com");
        var developerDto = new GithubUserDto(2L, "dev-user", "dev@test.com");

        // WHEN: Procesamos el login para el admin y luego para un developer.
         assertDoesNotThrow(() -> {
            authenticationService.processNewLogin(adminDto);
            authenticationService.processNewLogin(developerDto);
        }, "La secuencia de creación de usuarios no debería lanzar excepciones de persistencia.");

        // THEN: Verificamos que el estado final de la base de datos es el correcto
        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(roleRepository.count()).isEqualTo(RoleName.values().length); // Se deben haber creado solo 2 roles (ADMIN y DEVELOPER)
    }
}