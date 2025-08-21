package org.grubhart.pucp.tesis.module_administration;

import org.grubhart.pucp.tesis.module_domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private GithubClient githubClient;
    @Mock
    private Environment environment;

    @InjectMocks
    private AuthenticationService authenticationService;

    private GithubUserDto githubUserDto;
    private User existingUser;
    private Role developerRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        githubUserDto = new GithubUserDto(1L, "testuser", "test@example.com");
        existingUser = new User(1L, "testuser", "test@example.com");
        developerRole = new Role(RoleName.DEVELOPER);
        adminRole = new Role(RoleName.ADMIN);
    }

    // =====================================================================================
    // TESTS FOR EXISTING USERS
    // =====================================================================================

    @Test
    @DisplayName("HU-1 / AC-1.1: GIVEN an existing user logs in, WHEN processing login, THEN return the existing user")
    void processNewLogin_whenUserExists_shouldReturnExistingUser() {
        // GIVEN: An existing user is found in the repository
        when(userRepository.findByGithubUsernameIgnoreCase("testuser")).thenReturn(Optional.of(existingUser));

        // WHEN: The login is processed
        LoginProcessingResult result = authenticationService.processNewLogin(githubUserDto);

        // THEN: The result contains the existing user and is not marked as the first admin
        assertThat(result.user()).isEqualTo(existingUser);
        assertThat(result.isFirstAdmin()).isFalse();
        verify(userRepository, never()).save(any(User.class)); // Verify no new user is saved
    }

    // =====================================================================================
    // TESTS FOR INITIAL BOOTSTRAP (NO ADMIN EXISTS)
    // =====================================================================================

    @Test
    @DisplayName("HU-17 / AC-17.2: GIVEN no organization AND no admin, WHEN the initial admin logs in, THEN create user with ADMIN role")
    void processNewLogin_whenNoOrganizationAndIsAdminDuringBootstrap_shouldCreateAdminUser() {
        // GIVEN: No admin exists (initial bootstrap) and no organization is set
        when(userRepository.findByGithubUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(false);
        when(environment.getProperty("dora.github.organization-name")).thenReturn("");
        when(environment.getProperty("dora.initial-admin-username")).thenReturn("testuser");
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN: The initial admin logs in
        LoginProcessingResult result = authenticationService.processNewLogin(githubUserDto);

        // THEN: A new user is created with the ADMIN role
        assertThat(result.user().getGithubUsername()).isEqualTo("testuser");
        assertThat(result.user().getRoles()).contains(adminRole);
        assertThat(result.isFirstAdmin()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRoles()).extracting("name").containsExactly(RoleName.ADMIN);
    }

    @Test
    @DisplayName("HU-17 / AC-17.1: GIVEN no organization AND no admin, WHEN a non-admin user logs in, THEN access is denied")
    void processNewLogin_whenNoOrganizationAndNotAdminDuringBootstrap_shouldDenyAccess() {
        // GIVEN: No admin exists, no organization is set, and the user is NOT the initial admin
        when(userRepository.findByGithubUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(false);
        when(environment.getProperty("dora.github.organization-name")).thenReturn("");
        when(environment.getProperty("dora.initial-admin-username")).thenReturn("the-real-admin");

        // WHEN/THEN: Processing the login throws AccessDeniedException
        assertThatThrownBy(() -> authenticationService.processNewLogin(githubUserDto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not configured with an organization and you are not the initial administrator");
    }

    @Test
    @DisplayName("HU-17 / AC-17.3: GIVEN an organization is set AND no admin, WHEN a non-admin member logs in, THEN create user with DEVELOPER role")
    void processNewLogin_whenOrganizationExistsAndNotAdminDuringBootstrap_shouldCreateDeveloperUser() {
        // GIVEN: No admin exists, but an organization is set
        when(userRepository.findByGithubUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(false);
        when(environment.getProperty("dora.github.organization-name")).thenReturn("my-org");
        when(environment.getProperty("dora.initial-admin-username")).thenReturn("the-real-admin");
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(developerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN: A user who is not the initial admin logs in
        LoginProcessingResult result = authenticationService.processNewLogin(githubUserDto);

        // THEN: A new user is created with the DEVELOPER role
        assertThat(result.user().getGithubUsername()).isEqualTo("testuser");
        assertThat(result.user().getRoles()).contains(developerRole);
        assertThat(result.isFirstAdmin()).isFalse();
    }


    // =====================================================================================
    // TESTS FOR REGULAR LOGIN (ADMIN EXISTS)
    // =====================================================================================

    @Test
    @DisplayName("HU-1 / AC-1.3: GIVEN admin exists AND organization is set, WHEN a member logs in, THEN create user with DEVELOPER role")
    void processNewLogin_whenAdminExistsAndUserIsInOrg_shouldCreateDeveloperUser() {
        // GIVEN: An admin already exists (regular login) and the user is a member of the org
        when(userRepository.findByGithubUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(true);
        when(environment.getProperty("dora.github.organization-name")).thenReturn("my-org");
        when(githubClient.isUserMemberOfOrganization("testuser", "my-org")).thenReturn(true);
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(developerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN: The login is processed
        LoginProcessingResult result = authenticationService.processNewLogin(githubUserDto);

        // THEN: A new user is created with the DEVELOPER role
        assertThat(result.user().getGithubUsername()).isEqualTo("testuser");
        assertThat(result.user().getRoles()).contains(developerRole);
        assertThat(result.isFirstAdmin()).isFalse();
    }

    @Test
    @DisplayName("HU-1 / AC-1.4: GIVEN admin exists AND organization is set, WHEN a non-member logs in, THEN access is denied")
    void processNewLogin_whenAdminExistsAndUserIsNotInOrg_shouldDenyAccess() {
        // GIVEN: An admin exists and the user is NOT a member of the org
        when(userRepository.findByGithubUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(true);
        when(environment.getProperty("dora.github.organization-name")).thenReturn("my-org");
        when(githubClient.isUserMemberOfOrganization("testuser", "my-org")).thenReturn(false);

        // WHEN/THEN: Processing the login throws AccessDeniedException
        assertThatThrownBy(() -> authenticationService.processNewLogin(githubUserDto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not a member of the required organization");
    }

    @Test
    @DisplayName("HU-17 / AC-17.4: GIVEN admin exists AND no organization is set, WHEN a new user logs in, THEN access is denied")
    void processNewLogin_whenAdminExistsAndNoOrgSet_shouldDenyAccess() {
        // GIVEN: An admin exists but the organization is not configured
        when(userRepository.findByGithubUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(true);
        when(environment.getProperty("dora.github.organization-name")).thenReturn(""); // Blank organization

        // WHEN/THEN: Processing the login throws AccessDeniedException
        assertThatThrownBy(() -> authenticationService.processNewLogin(githubUserDto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("New users cannot be created because the organization is not defined");
    }

    @Test
    @DisplayName("GIVEN an existing user with roles, WHEN they log in, THEN their roles are preserved")
    void processNewLogin_withExistingUserWithRoles_shouldPreserveRoles() {
        // GIVEN: An existing user with a specific role (e.g., ADMIN)
        User adminUser = new User(1L, "testuser", "test@example.com");
        adminUser.setRoles(Set.of(adminRole));
        when(userRepository.findByGithubUsernameIgnoreCase("testuser")).thenReturn(Optional.of(adminUser));

        // WHEN: The user logs in
        LoginProcessingResult result = authenticationService.processNewLogin(githubUserDto);

        // THEN: The returned user has their original roles, and no save operation was performed
        assertThat(result.user().getRoles()).containsExactly(adminRole);
        verify(userRepository, never()).save(any(User.class));
    }


    @DisplayName("GIVEN initial bootstrap mode AND admin username is not set or blank, WHEN processing login, THEN throw IllegalStateException")
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"", "   "})
    void processNewLogin_whenInitialAdminNotSetOrBlank_shouldThrowException(String invalidAdminUsername) {
        // GIVEN: The initial admin username environment variable is invalid
        when(userRepository.findByGithubUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(false);
        when(environment.getProperty("dora.initial-admin-username")).thenReturn(invalidAdminUsername);

        // WHEN/THEN: A critical configuration error is thrown
        assertThatThrownBy(() -> authenticationService.processNewLogin(githubUserDto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("La configuración del administrador inicial (dora.initial-admin-username) no está definida");
    }


    @Test
    @DisplayName("GIVEN a new user is being created AND a required role is missing, WHEN processing login, THEN throw IllegalStateException")
    void processNewLogin_whenRequiredRoleIsMissing_shouldThrowException() {
        // GIVEN: The DEVELOPER role is missing from the database
        when(userRepository.findByGithubUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByRoles_Name(RoleName.ADMIN)).thenReturn(true); // Regular login mode
        when(environment.getProperty("dora.github.organization-name")).thenReturn("my-org");
        when(githubClient.isUserMemberOfOrganization(anyString(), anyString())).thenReturn(true);
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.empty()); // Role is missing

        // WHEN/THEN: A critical database integrity error is thrown
        assertThatThrownBy(() -> authenticationService.processNewLogin(githubUserDto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("El rol DEVELOPER no se encontró en la base de datos");
    }
}