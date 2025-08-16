package org.grubhart.pucp.tesis.api;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.grubhart.pucp.tesis.administration.AuthenticationService;
import org.grubhart.pucp.tesis.administration.GithubUserDto;
import org.grubhart.pucp.tesis.config.SecurityConfig;
import org.grubhart.pucp.tesis.domain.Role;
import org.grubhart.pucp.tesis.domain.RoleName;
import org.grubhart.pucp.tesis.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthenticationController.class)
@Import(SecurityConfig.class)
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    @DisplayName("POST /api/v1/auth/login debe procesar el usuario y devolver un UserDto")
    void login_whenUserIsProcessed_shouldReturnUserDto() throws Exception {
        //Given
        var githubUserDto = new GithubUserDto(123L, "test-user", "test@user.com");

        var processedUser = new User(1L, "test-user", "test@user.com");
        processedUser.getRoles().add(new Role(RoleName.DEVELOPER));
        when(authenticationService.processNewLogin(any(GithubUserDto.class))).thenReturn(processedUser);

        var expectedUserDto = new UserDto(processedUser.getId(), processedUser.getGithubUsername(), processedUser.getEmail(), Set.of("DEVELOPER"));
        //when & then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(githubUserDto)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(expectedUserDto)));

    }
}
