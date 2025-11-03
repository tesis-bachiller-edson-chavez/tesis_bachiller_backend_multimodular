package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_api.dto.UserSummaryDto;
import org.grubhart.pucp.tesis.module_domain.Role;
import org.grubhart.pucp.tesis.module_domain.RoleName;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

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

        User user1 = new User(1L, "activeuser", "active@test.com", "Active User", "url1");
        user1.getRoles().add(developerRole);

        User user2 = new User(2L, "anotheractive", "another@test.com", "Another User", "url2");
        user2.getRoles().add(developerRole);

        List<User> activeUsersFromRepo = List.of(user1, user2);

        when(userRepository.findAllByActiveTrue()).thenReturn(activeUsersFromRepo);

        // When
        List<UserSummaryDto> response = userController.getActiveUsers();

        // Then
        assertEquals(2, response.size());

        assertEquals("activeuser", response.get(0).githubUsername());
        assertEquals("Active User", response.get(0).name());
        assertEquals("url1", response.get(0).avatarUrl());
        assertNotNull(response.get(0).roles(), "Roles should not be null");
        assertTrue(response.get(0).roles().contains("DEVELOPER"), "Should contain DEVELOPER role");

        assertEquals("anotheractive", response.get(1).githubUsername());
        assertEquals("Another User", response.get(1).name());
        assertEquals("url2", response.get(1).avatarUrl());
        assertNotNull(response.get(1).roles(), "Roles should not be null");
        assertTrue(response.get(1).roles().contains("DEVELOPER"), "Should contain DEVELOPER role");
    }
}
