package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.OrganizationMember;
import org.grubhart.pucp.tesis.module_domain.Role;
import org.grubhart.pucp.tesis.module_domain.RoleName;
import org.grubhart.pucp.tesis.module_domain.RoleRepository;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
import org.grubhart.pucp.tesis.module_domain.GithubUserCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    @Mock
    private GithubUserCollector githubUserCollector;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    private UserSyncService userSyncService;

    @Captor
    private ArgumentCaptor<List<User>> userListCaptor;

    @BeforeEach
    void setUp() {
        userSyncService = new UserSyncService(githubUserCollector, userRepository, roleRepository, "test-org");
    }

    @Test
    void synchronizeUsers_shouldCreateNewUsers_whenTheyDoNotExistLocally() {
        // GIVEN
        var githubMember = new OrganizationMember(1L, "testuser", "http://avatar.url");
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(List.of(githubMember));
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        Role developerRole = new Role(RoleName.DEVELOPER);
        developerRole.setId(1L);
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(developerRole));

        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, times(1)).saveAll(userListCaptor.capture());

        List<User> savedUsers = userListCaptor.getValue();
        assertEquals(1, savedUsers.size());
        User savedUser = savedUsers.get(0);

        assertEquals(githubMember.id(), savedUser.getGithubId());
        assertEquals(githubMember.login(), savedUser.getGithubUsername());
        assertEquals(githubMember.avatarUrl(), savedUser.getAvatarUrl());
        assertTrue(savedUser.isActive());
    }

    @Test
    void synchronizeUsers_shouldDeactivateUsers_whenTheyAreNoLongerInTheOrganization() {
        // GIVEN
        User existingUser = new User();
        existingUser.setId(100L);
        existingUser.setGithubId(1L);
        existingUser.setGithubUsername("existingUser");
        existingUser.setAvatarUrl("http://avatar.url");
        existingUser.setActive(true);

        when(userRepository.findAll()).thenReturn(List.of(existingUser));
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(Collections.emptyList());

        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, times(1)).saveAll(userListCaptor.capture());

        List<User> updatedUsers = userListCaptor.getValue();
        assertEquals(1, updatedUsers.size());
        User updatedUser = updatedUsers.get(0);
        
        assertEquals(existingUser.getId(), updatedUser.getId());
        assertFalse(updatedUser.isActive());
    }

    @Test
    void synchronizeUsers_shouldUpdateExistingUserIfFound() {
        // GIVEN
        var githubMember = new OrganizationMember(1L, "testuser", "http://new-avatar.url");
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(List.of(githubMember));

        User existingUser = new User();
        existingUser.setId(100L);
        existingUser.setGithubId(1L);
        existingUser.setGithubUsername("testuser");
        existingUser.setAvatarUrl("http://old-avatar.url");
        existingUser.setActive(true);

        when(userRepository.findAll()).thenReturn(List.of(existingUser));

        Role developerRole = new Role(RoleName.DEVELOPER);
        developerRole.setId(1L);
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(developerRole));


        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, times(1)).saveAll(userListCaptor.capture());
        List<User> savedUsers = userListCaptor.getValue();
        assertEquals(1, savedUsers.size());
        User savedUser = savedUsers.get(0);


        assertEquals(existingUser.getId(), savedUser.getId());
        assertEquals("http://new-avatar.url", savedUser.getAvatarUrl());
        assertTrue(savedUser.isActive());
    }

    @Test
    void synchronizeUsers_shouldNotDeactivateAlreadyInactiveUser() {
        // GIVEN
        User inactiveUser = new User();
        inactiveUser.setId(101L);
        inactiveUser.setGithubId(2L);
        inactiveUser.setGithubUsername("inactiveUser");
        inactiveUser.setActive(false);

        when(userRepository.findAll()).thenReturn(List.of(inactiveUser));
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(Collections.emptyList());

        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, never()).saveAll(userListCaptor.capture());
    }

    @Test
    void scheduledSync_shouldTriggerSynchronizationWithConfiguredOrganization() {
        // GIVEN
        // No specific setup needed, the service is already configured in setUp()

        // WHEN
        userSyncService.scheduledSync();

        // THEN
        verify(githubUserCollector, times(1)).getOrganizationMembers("test-org");
    }

    @Test
    void synchronizeUsers_shouldAssignDeveloperRole_whenCreatingNewUser() {
        // GIVEN
        var githubMember = new OrganizationMember(1L, "newuser", "http://avatar.url");
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(List.of(githubMember));
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        Role developerRole = new Role(RoleName.DEVELOPER);
        developerRole.setId(1L);
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(developerRole));

        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, times(1)).saveAll(userListCaptor.capture());
        List<User> savedUsers = userListCaptor.getValue();
        assertEquals(1, savedUsers.size());

        User savedUser = savedUsers.get(0);
        assertFalse(savedUser.getRoles().isEmpty(), "User should have at least one role");
        assertTrue(savedUser.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.DEVELOPER),
                "User should have DEVELOPER role");
    }

    @Test
    void synchronizeUsers_shouldAssignDeveloperRole_whenExistingUserHasNoRoles() {
        // GIVEN
        var githubMember = new OrganizationMember(1L, "existinguser", "http://avatar.url");
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(List.of(githubMember));

        User existingUserWithoutRoles = new User();
        existingUserWithoutRoles.setId(100L);
        existingUserWithoutRoles.setGithubId(1L);
        existingUserWithoutRoles.setGithubUsername("existinguser");
        existingUserWithoutRoles.setActive(true);
        // roles es un HashSet vacío por defecto

        when(userRepository.findAll()).thenReturn(List.of(existingUserWithoutRoles));

        Role developerRole = new Role(RoleName.DEVELOPER);
        developerRole.setId(1L);
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(developerRole));

        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, times(1)).saveAll(userListCaptor.capture());
        List<User> savedUsers = userListCaptor.getValue();
        assertEquals(1, savedUsers.size());

        User savedUser = savedUsers.get(0);
        assertFalse(savedUser.getRoles().isEmpty(), "User should have at least one role");
        assertTrue(savedUser.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.DEVELOPER),
                "User should have DEVELOPER role");
    }

    @Test
    void synchronizeUsers_shouldNotDuplicateDeveloperRole_whenUserAlreadyHasIt() {
        // GIVEN
        var githubMember = new OrganizationMember(1L, "userwithrole", "http://avatar.url");
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(List.of(githubMember));

        Role developerRole = new Role(RoleName.DEVELOPER);
        developerRole.setId(1L);

        User existingUserWithRole = new User();
        existingUserWithRole.setId(100L);
        existingUserWithRole.setGithubId(1L);
        existingUserWithRole.setGithubUsername("userwithrole");
        existingUserWithRole.setActive(true);
        existingUserWithRole.getRoles().add(developerRole); // Ya tiene el rol

        when(userRepository.findAll()).thenReturn(List.of(existingUserWithRole));
        // Usamos lenient() porque este stub podría no ser usado si el usuario ya tiene el rol
        lenient().when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(developerRole));

        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, times(1)).saveAll(userListCaptor.capture());
        List<User> savedUsers = userListCaptor.getValue();
        assertEquals(1, savedUsers.size());

        User savedUser = savedUsers.get(0);
        assertEquals(1, savedUser.getRoles().size(), "User should have exactly one role (no duplicates)");
        assertTrue(savedUser.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.DEVELOPER),
                "User should still have DEVELOPER role");
    }

    @Test
    void ensureUserHasDeveloperRole_shouldNotAddRole_whenUserAlreadyHasDeveloperRole() {
        // GIVEN
        Role techLeadRole = new Role(RoleName.TECH_LEAD);
        techLeadRole.setId(1L);

        Role developerRole = new Role(RoleName.DEVELOPER);
        developerRole.setId(2L);

        User userWithMultipleRoles = new User();
        userWithMultipleRoles.setGithubId(1L);
        userWithMultipleRoles.setGithubUsername("testuser");
        // Usuario tiene múltiples roles incluyendo DEVELOPER
        // Esto fuerza al anyMatch a ejecutarse múltiples veces, cubriendo ambos branches del lambda
        userWithMultipleRoles.getRoles().add(techLeadRole);
        userWithMultipleRoles.getRoles().add(developerRole);

        // WHEN
        userSyncService.ensureUserHasDeveloperRole(userWithMultipleRoles);

        // THEN
        assertEquals(2, userWithMultipleRoles.getRoles().size(), "Should still have exactly two roles");
        assertTrue(userWithMultipleRoles.getRoles().contains(developerRole), "Should still have DEVELOPER role");
        assertTrue(userWithMultipleRoles.getRoles().contains(techLeadRole), "Should still have TECH_LEAD role");
        // Verificar que NO se llamó al repository (porque ya tenía el rol)
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void ensureUserHasDeveloperRole_shouldAddRole_whenUserHasOtherRolesButNotDeveloper() {
        // GIVEN
        Role techLeadRole = new Role(RoleName.TECH_LEAD);
        techLeadRole.setId(1L);

        Role developerRole = new Role(RoleName.DEVELOPER);
        developerRole.setId(2L);

        User userWithoutDeveloperRole = new User();
        userWithoutDeveloperRole.setGithubId(1L);
        userWithoutDeveloperRole.setGithubUsername("testuser");
        // Usuario tiene solo TECH_LEAD, sin DEVELOPER
        // Esto garantiza que anyMatch evalúe el lambda a false y recorra todos los roles
        userWithoutDeveloperRole.getRoles().add(techLeadRole);

        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(developerRole));

        // WHEN
        userSyncService.ensureUserHasDeveloperRole(userWithoutDeveloperRole);

        // THEN
        assertEquals(2, userWithoutDeveloperRole.getRoles().size(), "Should now have two roles");
        assertTrue(userWithoutDeveloperRole.getRoles().contains(developerRole), "Should now have DEVELOPER role");
        assertTrue(userWithoutDeveloperRole.getRoles().contains(techLeadRole), "Should still have TECH_LEAD role");
        verify(roleRepository, times(1)).findByName(RoleName.DEVELOPER);
    }
}
