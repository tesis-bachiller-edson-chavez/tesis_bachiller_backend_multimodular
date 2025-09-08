package org.grubhart.pucp.tesis.module_administration;

import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.GithubUserAuthenticator;
import org.grubhart.pucp.tesis.module_domain.RoleName;
import org.grubhart.pucp.tesis.module_domain.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DataInitializerTest {

    @MockitoBean
    private GithubUserAuthenticator githubUserAuthenticator;

    @MockitoBean
    private GithubCommitCollector githubCommitCollector;

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Cuando DataInitializer se ejecuta por segunda vez, no debe crear roles duplicados")
    void run_whenRolesAlreadyExist_shouldNotCreateDuplicates() throws Exception {
        // Arrange: El contexto de Spring ya se ha cargado, y DataInitializer.run() ya se ha ejecutado una vez
        // como parte del arranque de la aplicación. Por lo tanto, los roles ya deberían existir.
        long initialRoleCount = roleRepository.count();
        assertThat(initialRoleCount).isEqualTo(RoleName.values().length);

        // Act: Ejecutamos el método run() manualmente por segunda vez.
        // Esta ejecución cubrirá la rama del 'if' donde los roles ya existen.
        dataInitializer.run(null); // Pasamos null porque no usamos los ApplicationArguments en la lógica.

        // Assert: Verificamos que no se han añadido nuevos roles.
        // El contador de roles debe seguir siendo el mismo.
        assertThat(roleRepository.count()).isEqualTo(initialRoleCount);
    }
}