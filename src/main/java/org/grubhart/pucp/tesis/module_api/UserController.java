package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_api.dto.UserSummaryDto;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users") // Corregido para que el endpoint sea /api/v1/users
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof OAuth2User)) {
            logger.error("El principal de la autenticación no es de tipo OAuth2User. Tipo actual: {}",
                    authentication.getPrincipal().getClass().getName());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String username = oauthUser.getAttribute("login");

        logger.debug("Buscando datos para el usuario autenticado: {}", username);
        return userRepository.findByGithubUsernameIgnoreCase(username)
                .map(this::mapToUserDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    logger.warn("Usuario autenticado '{}' no encontrado en la base de datos.", username);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                });
    }

    @GetMapping
    public List<UserSummaryDto> getActiveUsers() {
        return userRepository.findAllByActiveTrue().stream()
                .map(this::mapToUserSummaryDto)
                .collect(Collectors.toList());
    }

    private UserDto mapToUserDto(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());
        return new UserDto(user.getId(), user.getGithubUsername(), user.getEmail(), roleNames);
    }

    private UserSummaryDto mapToUserSummaryDto(User user) {
        return new UserSummaryDto(user.getGithubUsername(), user.getName(), user.getAvatarUrl());
    }
}
