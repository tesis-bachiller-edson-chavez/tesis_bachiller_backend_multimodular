package org.grubhart.pucp.tesis.module_api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("GIVEN an authenticated user WHEN they access /logout THEN the session is invalidated and an OK status is returned")
    void logout_whenAuthenticated_shouldInvalidateSessionAndReturnOk() throws Exception {
        // This test covers the `if (authentication != null && authentication.getName() != null)` branch
        // WHEN: The authenticated user performs a POST request to /logout
        mockMvc.perform(post("/logout").with(csrf()))
                // THEN: The request is successful and returns an OK status
                .andExpect(status().isOk())
                // AND: The user is no longer authenticated
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("GIVEN a logout request with no prior authentication WHEN they access /logout THEN an OK status is returned")
    void logout_whenNotAuthenticated_shouldReturnOk() throws Exception {
        // This test covers the `else` branch, where the authentication object is null
        // WHEN: An unauthenticated user performs a POST request to /logout
        mockMvc.perform(post("/logout").with(csrf()))
                // THEN: The request is successful and returns an OK status
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GIVEN an authenticated user with no name, WHEN they access /logout, THEN the session is invalidated and an OK status is returned")
    void logout_whenAuthenticationHasNoName_shouldReturnOk() throws Exception {
        // GIVEN: An authentication object that is authenticated but has a null name
        // This covers the path where authentication != null but authentication.getName() == null
        Authentication authWithNoName = Mockito.mock(Authentication.class);
        Mockito.when(authWithNoName.isAuthenticated()).thenReturn(true);
        Mockito.when(authWithNoName.getName()).thenReturn(null);

        // WHEN: The user with this authentication performs a POST request to /logout
        mockMvc.perform(post("/logout")
                        .with(authentication(authWithNoName)) // Set the mock authentication for this request
                        .with(csrf()))
                // THEN: The request is successful and returns an OK status
                .andExpect(status().isOk())
                // AND: The user is no longer authenticated
                .andExpect(unauthenticated());
    }
}
