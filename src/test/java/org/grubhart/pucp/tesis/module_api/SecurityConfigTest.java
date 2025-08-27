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
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("AC 2.1: GIVEN an authenticated user, WHEN they access /logout, THEN the session is invalidated and an OK status is returned")
    void logout_whenAuthenticated_shouldInvalidateSessionAndReturnOk() throws Exception {
        // WHEN: The authenticated user performs a POST request to /logout
        mockMvc.perform(post("/logout").with(csrf()))
                // THEN: The request is successful and returns an OK status
                .andExpect(status().isOk())
                // AND: The user is no longer authenticated
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("GIVEN an authenticated user with no name, WHEN they access /logout, THEN the session is invalidated and an OK status is returned")
    void logout_whenAuthenticationHasNoName_shouldReturnOk() throws Exception {
        // GIVEN: An authentication object that is authenticated but has a null name
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
