package org.grubhart.pucp.tesis.module_api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.grubhart.pucp.tesis.module_administration.AuthenticationService;
import org.grubhart.pucp.tesis.module_administration.GithubUserDto;
import org.grubhart.pucp.tesis.module_administration.LoginProcessingResult;
import org.grubhart.pucp.tesis.module_domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * This class is a unit test for Oauth2LoginSuccessHandler.
 * It was formerly testing SecurityConfig, but has been refactored
 * to test the handler logic in isolation for speed and simplicity.
 * The file was moved from src/main/java to src/test/java.
 */
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest { // Renamed from Oauth2LoginSuccessHandlerTest to match filename

    @Mock
    private AuthenticationService authenticationService;

    private Oauth2LoginSuccessHandler successHandler;

    // For a unit test, we can provide a hardcoded value for the dependency.
    private final String frontendUrl = "http://test-frontend.com";

    @BeforeEach
    void setUp() {
        // The System Under Test (SUT) is instantiated directly, providing its mock dependencies.
        successHandler = new Oauth2LoginSuccessHandler(authenticationService, frontendUrl);
    }

    @Test
    @DisplayName("GIVEN a successful OAuth2 login WHEN the user is not the first admin THEN it should redirect to the frontend home page")
    void onAuthenticationSuccess_whenNotFirstAdmin_shouldRedirectToFrontendHome() throws Exception {
        // GIVEN
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Authentication authentication = mock(Authentication.class);
        OAuth2User oauthUser = new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("id", 123L, "login", "testuser", "email", "test@example.com"),
                "login"
        );
        User testUser = new User(123L, "testuser", "test@example.com");

        when(authentication.getPrincipal()).thenReturn(oauthUser);
        when(authenticationService.processNewLogin(any(GithubUserDto.class)))
                .thenReturn(new LoginProcessingResult(testUser, false));

        // WHEN
        // The method is called directly on our handler instance
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // THEN
        verify(response).sendRedirect(frontendUrl + "/home");
    }

    @Test
    @DisplayName("GIVEN a successful OAuth2 login WHEN the user is the first admin THEN it should redirect to the admin setup page")
    void onAuthenticationSuccess_whenFirstAdmin_shouldRedirectToAdminSetup() throws Exception {
        // GIVEN
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Authentication authentication = mock(Authentication.class);
        OAuth2User oauthUser = new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("id", 123L, "login", "testuser", "email", "test@example.com"),
                "login"
        );
        User testUser = new User(123L, "testuser", "test@example.com");

        when(authentication.getPrincipal()).thenReturn(oauthUser);
        when(authenticationService.processNewLogin(any(GithubUserDto.class)))
                .thenReturn(new LoginProcessingResult(testUser, true));

        // WHEN
        // The method is called directly on our handler instance
        successHandler.onAuthenticationSuccess(request, response, authentication);

        // THEN
        verify(response).sendRedirect(frontendUrl + "/admin/setup");
    }
}
