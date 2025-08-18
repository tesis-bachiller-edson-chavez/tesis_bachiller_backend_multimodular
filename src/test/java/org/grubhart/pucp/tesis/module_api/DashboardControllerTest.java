package org.grubhart.pucp.tesis.module_api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerTest {

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController();
    }

    @Test
    @DisplayName("S (Happy Path): showDashboard con usuario OAuth2 válido debe mostrar el username")
    void showDashboard_withValidOAuth2User_shouldShowUsername() {
        // GIVEN
        Map<String, Object> attributes = Map.of("login", "test-user");
        OAuth2User oauthUser = new DefaultOAuth2User(Collections.emptyList(), attributes, "login");
        Authentication authentication = new OAuth2AuthenticationToken(oauthUser, Collections.emptyList(), "github");

        // WHEN
        ResponseEntity<String> response = controller.showDashboard(authentication);

        // THEN
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("¡Bienvenido, test-user!");
    }

    @Test
    @DisplayName("Z (Zero): showDashboard con autenticación nula debe mostrar el username por defecto")
    void showDashboard_withNullAuthentication_shouldShowDefaultUsername() {
        // GIVEN: authentication is null

        // WHEN
        ResponseEntity<String> response = controller.showDashboard(null);

        // THEN
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("¡Bienvenido, Usuario!");
    }

    @Test
    @DisplayName("I (Interface): showDashboard con un principal que no es OAuth2User debe mostrar el username por defecto")
    void showDashboard_withNonOAuth2UserPrincipal_shouldShowDefaultUsername() {
        // GIVEN: An authentication object with a principal that is not an OAuth2User
        Authentication authentication = new UsernamePasswordAuthenticationToken("some-user", "password");

        // WHEN
        ResponseEntity<String> response = controller.showDashboard(authentication);

        // THEN
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("¡Bienvenido, Usuario!");
    }
}