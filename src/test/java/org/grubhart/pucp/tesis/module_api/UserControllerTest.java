package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_domain.Role;
import org.grubhart.pucp.tesis.module_domain.RoleName;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

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

    @BeforeEach
    void setUp() {
        when(authentication.getPrincipal()).thenReturn(oAuth2User);
    }

    @Test
    void getCurrentUser_whenUserExists_shouldReturnUserDto() {
        // Given
        String username = "testuser";
        String email = "test@example.com";
        when(oAuth2User.getAttribute("login")).thenReturn(username);

        // Mock the User object to isolate the test from its implementation
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
}
