package org.grubhart.pucp.tesis.administration;

import org.grubhart.pucp.tesis.domain.Role;
import org.grubhart.pucp.tesis.domain.RoleName;
import org.grubhart.pucp.tesis.domain.User;
import org.grubhart.pucp.tesis.domain.UserRepository;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final Environment environment;

    public AuthenticationService(UserRepository userRepository, Environment environment) {
        this.userRepository = userRepository;
        this.environment = environment;
    }

    public User processNewLogin(GithubUserDto githubUser) {
        // Si no hay administrador, manejamos el caso especial de arranque inicial.
        if (!userRepository.existsByRoles_Name(RoleName.ADMIN)) {
            return handleInitialBootstrap(githubUser);
        }
        
        // Si el sistema ya está configurado, procesamos un login normal.
        return userRepository.findByGithubUsernameIgnoreCase(githubUser.username())
                .orElseGet(() -> createNewUserWithDefaultRole(githubUser));
    }

    /**
     * Maneja la lógica de creación del primer usuario en el sistema.
     * Asigna el rol de ADMIN o DEVELOPER según la configuración.
     */
    private User handleInitialBootstrap(GithubUserDto githubUser) {
        String initialAdminUsername = environment.getProperty("dora.initial-admin-username");

        if (initialAdminUsername == null || initialAdminUsername.trim().isEmpty()) {
            throw new IllegalStateException("La configuración del administrador inicial (dora.initial-admin-username) no está definida. El sistema no puede arrancar de forma segura.");
        }

        if (githubUser.username().equalsIgnoreCase(initialAdminUsername)) {
            User adminUser = new User(githubUser.id(), githubUser.username(), githubUser.email());
            adminUser.getRoles().add(new Role(RoleName.ADMIN));
            return userRepository.save(adminUser);
        } else {
            return createNewUserWithDefaultRole(githubUser);
        }
    }

    /**
     * Crea un nuevo usuario, le asigna el rol por defecto DEVELOPER y lo guarda.
     */
    private User createNewUserWithDefaultRole(GithubUserDto githubUser) {
        User newUser = new User(githubUser.id(), githubUser.username(), githubUser.email());
        newUser.getRoles().add(new Role(RoleName.DEVELOPER));
        return userRepository.save(newUser);
    }
}
