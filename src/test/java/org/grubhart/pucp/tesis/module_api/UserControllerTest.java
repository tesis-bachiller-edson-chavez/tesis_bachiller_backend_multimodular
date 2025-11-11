package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_api.dto.AssignRolesRequest;
import org.grubhart.pucp.tesis.module_api.dto.UserSummaryDto;
import org.grubhart.pucp.tesis.module_domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private UserController userController;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oAuth2User;

    @Test
    void getCurrentUser_whenUserExists_shouldReturnUserDto() {
        // Given
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        String username = "testuser";
        String email = "test@example.com";
        when(oAuth2User.getAttribute("login")).thenReturn(username);

        User userMock = mock(User.class);
        when(userMock.getId()).thenReturn(1L);
        when(userMock.getGithubId()).thenReturn(123456L);
        when(userMock.getGithubUsername()).thenReturn(username);
        when(userMock.getEmail()).thenReturn(email);

        Role userRole = new Role();
        userRole.setName(RoleName.DEVELOPER);
        when(userMock.getRoles()).thenReturn(Set.of(userRole));

        when(userRepository.findByGithubUsernameIgnoreCase(username)).thenReturn(Optional.of(userMock));

        // When
        ResponseEntity<UserDto> response = userController.getCurrentUser(authentication);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserDto userDto = response.getBody();
        assertNotNull(userDto);
        assertEquals(1L, userDto.id());
        assertEquals(123456L, userDto.githubId());
        assertEquals(username, userDto.githubUsername());
        assertEquals(email, userDto.email());
        assertTrue(userDto.roles().contains("DEVELOPER"));
    }

    @Test
    void getCurrentUser_whenUserDoesNotExist_shouldReturnNotFound() {
        // Given
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        String username = "nonexistent";
        when(oAuth2User.getAttribute("login")).thenReturn(username);
        when(userRepository.findByGithubUsernameIgnoreCase(username)).thenReturn(Optional.empty());

        // When
        ResponseEntity<UserDto> response = userController.getCurrentUser(authentication);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void getCurrentUser_whenPrincipalIsNotOAuth2User_shouldReturnInternalServerError() {
        // Given
        when(authentication.getPrincipal()).thenReturn(new Object()); // Not an OAuth2User

        // When
        ResponseEntity<UserDto> response = userController.getCurrentUser(authentication);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void getActiveUsers_shouldReturnListOfActiveUserSummaries() {
        // Given
        Role developerRole = new Role(RoleName.DEVELOPER);

        User user1 = new User(100L, "activeuser", "active@test.com", "Active User", "url1");
        user1.setId(1L);
        user1.getRoles().add(developerRole);

        User user2 = new User(200L, "anotheractive", "another@test.com", "Another User", "url2");
        user2.setId(2L);
        user2.getRoles().add(developerRole);

        List<User> activeUsersFromRepo = List.of(user1, user2);

        when(userRepository.findAllByActiveTrue()).thenReturn(activeUsersFromRepo);

        // When
        List<UserSummaryDto> response = userController.getActiveUsers();

        // Then
        assertEquals(2, response.size());

        assertEquals(1L, response.get(0).id());
        assertEquals(100L, response.get(0).githubId());
        assertEquals("activeuser", response.get(0).githubUsername());
        assertEquals("Active User", response.get(0).name());
        assertEquals("url1", response.get(0).avatarUrl());
        assertNotNull(response.get(0).roles(), "Roles should not be null");
        assertTrue(response.get(0).roles().contains("DEVELOPER"), "Should contain DEVELOPER role");

        assertEquals(2L, response.get(1).id());
        assertEquals(200L, response.get(1).githubId());
        assertEquals("anotheractive", response.get(1).githubUsername());
        assertEquals("Another User", response.get(1).name());
        assertEquals("url2", response.get(1).avatarUrl());
        assertNotNull(response.get(1).roles(), "Roles should not be null");
        assertTrue(response.get(1).roles().contains("DEVELOPER"), "Should contain DEVELOPER role");
    }

    @Test
    void assignRoles_whenUserExistsAndRolesValid_shouldAssignRolesSuccessfully() {
        // Given
        Long userId = 1L;
        Set<String> rolesToAssign = Set.of("TECH_LEAD", "DEVELOPER");
        AssignRolesRequest request = new AssignRolesRequest(rolesToAssign);

        User user = new User(100L, "testuser", "test@example.com", "Test User", "avatar.jpg");
        user.setId(userId);

        Role techLeadRole = new Role(RoleName.TECH_LEAD);
        Role developerRole = new Role(RoleName.DEVELOPER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(RoleName.TECH_LEAD)).thenReturn(Optional.of(techLeadRole));
        when(roleRepository.findByName(RoleName.DEVELOPER)).thenReturn(Optional.of(developerRole));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        ResponseEntity<UserSummaryDto> response = userController.assignRoles(userId, request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(userRepository).save(user);
        assertTrue(user.getRoles().contains(techLeadRole));
        assertTrue(user.getRoles().contains(developerRole));
        assertEquals(2, user.getRoles().size());
    }

    @Test
    void assignRoles_whenUserNotFound_shouldThrowException() {
        // Given
        Long userId = 999L;
        AssignRolesRequest request = new AssignRolesRequest(Set.of("DEVELOPER"));

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            userController.assignRoles(userId, request);
        });
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void assignRoles_whenInvalidRoleProvided_shouldThrowException() {
        // Given
        Long userId = 1L;
        Set<String> invalidRoles = Set.of("INVALID_ROLE");
        AssignRolesRequest request = new AssignRolesRequest(invalidRoles);

        User user = new User(100L, "testuser", "test@example.com", "Test User", "avatar.jpg");
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            userController.assignRoles(userId, request);
        });
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void assignRoles_whenRoleNotFoundInDatabase_shouldThrowException() {
        // Given
        Long userId = 1L;
        AssignRolesRequest request = new AssignRolesRequest(Set.of("ADMIN"));

        User user = new User(100L, "testuser", "test@example.com", "Test User", "avatar.jpg");
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            userController.assignRoles(userId, request);
        });
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void assignRoles_shouldReplaceExistingRoles() {
        // Given
        Long userId = 1L;
        Set<String> newRoles = Set.of("ADMIN");
        AssignRolesRequest request = new AssignRolesRequest(newRoles);

        User user = new User(100L, "testuser", "test@example.com", "Test User", "avatar.jpg");
        user.setId(userId);

        // Usuario ya tiene rol DEVELOPER
        Role existingRole = new Role(RoleName.DEVELOPER);
        user.getRoles().add(existingRole);

        Role adminRole = new Role(RoleName.ADMIN);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenReturn(user);

        // When
        ResponseEntity<UserSummaryDto> response = userController.assignRoles(userId, request);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        verify(userRepository).save(user);
        assertTrue(user.getRoles().contains(adminRole));
        assertFalse(user.getRoles().contains(existingRole));
        assertEquals(1, user.getRoles().size());
    }
}
