package org.grubhart.pucp.tesis.administration;

import org.grubhart.pucp.tesis.domain.Role;
import org.grubhart.pucp.tesis.domain.RoleName;
import org.grubhart.pucp.tesis.domain.User;
import org.grubhart.pucp.tesis.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Environment environment;

    private AuthenticationService authenticationService;

    private final String INITIAL_ADMIN_USERNAME = "edson";

    @BeforeEach
    void setUp() {
        // Instanciamos manualmente el servicio con sus dependencias mockeadas
        authenticationService = new AuthenticationService(userRepository, environment);

        lenient().when(environment.getProperty("dora.initial-admin-username"))
                .thenReturn(INITIAL_ADMIN_USERNAME);

        lenient().when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Z (Zero): Lanza excepción si no hay admin y la variable de entorno no está configurada")
    void processNewLogin_whenNoAdminAndNoEnvVar_shouldThrowException() {
        // GIVEN
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(false);
        // Simulamos que la variable de entorno no existe
        when(environment.getProperty("dora.initial-admin-username")).thenReturn(null);

        var githubUser = new GithubUserDto(123L, "any-user", "any@github.com");

        // WHEN & THEN
        // Verificamos que se lanza la excepción esperada
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            authenticationService.processNewLogin(githubUser);
        }, "Debería lanzarse una excepción si el admin inicial no está configurado");
    }

    @Test
    @DisplayName("O (One): Un nuevo usuario no administrador obtiene el rol por defecto")
    void processNewLogin_whenFirstUserIsNotAdmin_shouldAssignDefaultRole() {
        // GIVEN
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(false);
        var githubUser = new GithubUserDto(456L, "a-regular-developer", "dev@github.com");

        // WHEN
        authenticationService.processNewLogin(githubUser);

        // THEN
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getGithubUsername()).isEqualTo("a-regular-developer");
        assertThat(savedUser.getRoles()).extracting(Role::getName).containsExactly(RoleName.DEVELOPER);
    }


    @Test
    @DisplayName("S (Happy Path): El primer usuario coincidente se convierte en administrador")
    void processNewLogin_whenFirstUserIsAdmin_shouldAssignAdminRole() {
        // GIVEN
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(false);
        var githubUser = new GithubUserDto(123L, INITIAL_ADMIN_USERNAME, "edson@github.com");

        // WHEN
        authenticationService.processNewLogin(githubUser);

        // THEN
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getGithubUsername()).isEqualTo(INITIAL_ADMIN_USERNAME);
        assertThat(savedUser.getRoles()).extracting(Role::getName).containsExactly(RoleName.ADMIN);
    }

    @Test
    @DisplayName("M (Many): Un nuevo usuario en un sistema ya configurado obtiene el rol por defecto")
    void processNewLogin_whenAdminExistsAndNewUserLogsIn_shouldAssignDefaultRole() {
        // GIVEN
        // 1. Ya existe un administrador en el sistema
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(true);
        // 2. El usuario que se loguea no existe todavía
        when(userRepository.findByGithubUsernameIgnoreCase("new-developer")).thenReturn(java.util.Optional.empty());

        var githubUser = new GithubUserDto(789L, "new-developer", "new-dev@github.com");

        // WHEN
        authenticationService.processNewLogin(githubUser);

        // THEN
        // 1. Se debe haber guardado un nuevo usuario
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        // 2. El usuario guardado debe tener el rol de DEVELOPER
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getGithubUsername()).isEqualTo("new-developer");
        assertThat(savedUser.getRoles()).extracting(Role::getName).containsExactly(RoleName.DEVELOPER);
    }

    @Test
    @DisplayName("Z (Zero): lanza excepcion si la variable de entorno del admin esta vacia")
    void processNewLogin_whenNoAdminAndEmptyEnvVar_shouldThrowException() {
        //Given
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(false);
        //Simulamos que la variable de entorno esta presente pero esta vacia
        when(environment.getProperty("dora.initial-admin-username")).thenReturn("    ");

        var githubUser = new GithubUserDto(123L, "any-user", "any@github.com");

        //When & Then
        //Verificamos que se lanza la excepcion
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            authenticationService.processNewLogin(githubUser);
        }, "Deberia lanzarse una excepcion, si el admin inicial esta configurado como vacio");
    }

    @Test
    @DisplayName("M (Many): Un usuario existente que vuelve a iniciar sesion es recuperado correctamente")
    void processNewLogin_whenAdminExistsAndExistingUserLogsIn_ShouldReturnExistingUser(){
        //Given
        // 1. Ya existe un Administrador en el sistema
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(true);
        //2. El usuario que se loguea ya existe en la BD
        User existingUser = new User(999L, "existing-user", "existing@github.com");
        existingUser.getRoles().add(new Role(RoleName.DEVELOPER));
        when(userRepository.findByGithubUsernameIgnoreCase("existing-user")).thenReturn(java.util.Optional.of(existingUser));

        var githubUserDto = new GithubUserDto(999L, "existing-user", "existing@github.com");
        //WHEN
        User returnedUser = authenticationService.processNewLogin(githubUserDto);
        //THEN
        //1. El usuario devuelto debe ser el mismo que el existente
        assertThat(returnedUser).isEqualTo(existingUser);
        //2. NO se debe haber llamado a save(), porque el usuario ya existia
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }

}