package org.grubhart.pucp.tesis.api;

import org.grubhart.pucp.tesis.administration.AuthenticationService;
import org.grubhart.pucp.tesis.administration.GithubUserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserDto> login(@RequestBody GithubUserDto githubUserDto) {
        var processedUser = authenticationService.processNewLogin(githubUserDto);

        var userDto = new UserDto(processedUser.getId(),processedUser.getGithubUsername(),processedUser.getEmail(),
                processedUser.getRoles().stream().map(role -> role.getName().toString()).collect(Collectors.toSet()));
        return ResponseEntity.ok(userDto);
    }
}
