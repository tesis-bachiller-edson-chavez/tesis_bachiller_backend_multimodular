package org.grubhart.pucp.tesis.module_api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("AC 2.1: GIVEN an authenticated user, WHEN they access /logout, THEN the session is invalidated and they are redirected to the root")
    void logout_whenAuthenticated_shouldInvalidateSessionAndRedirect() throws Exception {
        // WHEN: The authenticated user performs a POST request to /logout
        mockMvc.perform(post("/logout").with(csrf()))
                // THEN: The request is successful and results in a redirection
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                // AND: The user is no longer authenticated
                .andExpect(unauthenticated());
    }
}
