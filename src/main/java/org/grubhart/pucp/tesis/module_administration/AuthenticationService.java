package org.grubhart.pucp.tesis.module_administration;

import jakarta.transaction.Transactional;
import org.grubhart.pucp.tesis.module_domain.RoleRepository;
import org.grubhart.pucp.tesis.module_domain.Role;
import org.grubhart.pucp.tesis.module_domain.RoleName;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
import org.springframework.core.env.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final Environment environment;
    private final RoleRepository roleRepository;
    private final GithubClient githubClient;



    public AuthenticationService(UserRepository userRepository, Environment environment, RoleRepository roleRepository, GithubClient githubClient) {
        this.userRepository = userRepository;
        this.environment = environment;
        this.roleRepository = roleRepository;
        this.githubClient = githubClient;
    }

    @Transactional
    public LoginProcessingResult processNewLogin(GithubUserDto githubUser) {
        logger.info("Procesando login para el usuario de GitHub: {}", githubUser.username());

        validateOrganizationMembership(githubUser);


        if (isInitialBootstrap())  {
            logger.debug("No se encontró ningún administrador. Ejecutando lógica de arranque inicial.");
            User bootstrappedUser = handleInitialBootstrap(githubUser);
            boolean isFirstAdmin = bootstrappedUser.getRoles().stream()
                    .anyMatch(role -> role.getName() == RoleName.ADMIN);
            return new LoginProcessingResult(bootstrappedUser, isFirstAdmin);
        }
        
        logger.debug("Sistema ya inicializado. Procesando login normal.");
        User user = findOrCreateUser(githubUser);
        return new LoginProcessingResult(user, false);
    }

    private void validateOrganizationMembership(GithubUserDto githubUser) {
        String organizationName = environment.getProperty("dora.github.organization-name");
        if (organizationName == null || organizationName.isBlank()) {
            return; // No hay organización configurada, no se requiere validación.
        }

        // La validación solo se aplica si el sistema ya tiene un administrador (no es el bootstrap inicial).
        if (!isInitialBootstrap()) {
            boolean isMember = githubClient.isUserMemberOfOrganization(githubUser.username(), organizationName);
            if (!isMember) {
                throw new AccessDeniedException("Acceso denegado: El usuario '" + githubUser.username() + "' no es miembro de la organización '" + organizationName + "'.");
            }
        }
    }

    private boolean isInitialBootstrap() {
        return !userRepository.existsByRoles_Name(RoleName.ADMIN);
    }

    private User findOrCreateUser(GithubUserDto githubUser) {
        return userRepository.findByGithubUsernameIgnoreCase(githubUser.username())
                .map(existingUser -> {
                    logger.info("Usuario '{}' ya existe en la base de datos. Devolviendo usuario existente.", existingUser.getGithubUsername());
                    return existingUser;
                }).orElseGet(() -> {
                    logger.info("Usuario '{}' no encontrado. Creando nuevo usuario con rol por defecto.", githubUser.username());
                    return createNewUserWithDefaultRole(githubUser);
                });
    }


    private User handleInitialBootstrap(GithubUserDto githubUser) {
        logger.debug("Dentro de handleInitialBootstrap para el usuario '{}'", githubUser.username());
        String initialAdminUsername = environment.getProperty("dora.initial-admin-username");
        logger.debug("Valor de 'dora.initial-admin-username': '{}'", initialAdminUsername);

        if (initialAdminUsername == null || initialAdminUsername.trim().isEmpty()) {
            logger.error("La variable de entorno 'dora.initial-admin-username' no está configurada o está vacía.");
            throw new IllegalStateException("La configuración del administrador inicial (dora.initial-admin-username) no está definida. El sistema no puede arrancar de forma segura.");
        }

        if (githubUser.username().equalsIgnoreCase(initialAdminUsername)) {
            logger.info("El usuario '{}' coincide con el administrador inicial. Asignando rol ADMIN.", githubUser.username());
            return createUserWithRole(githubUser, RoleName.ADMIN);
        } else {
            logger.info("El usuario '{}' NO coincide con el administrador inicial. Asignando rol por defecto DEVELOPER.", githubUser.username());
            return createNewUserWithDefaultRole(githubUser);
        }
    }


    private User createNewUserWithDefaultRole(GithubUserDto githubUser) {
        return createUserWithRole(githubUser, RoleName.DEVELOPER);
    }

    private User createUserWithRole(GithubUserDto githubUser, RoleName roleName) {
        logger.debug("Creando nuevo usuario '{}' con rol {}.", githubUser.username(), roleName);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("El rol " + roleName + " no se encontró en la base de datos. La inicialización falló."));

        User newUser = new User(githubUser.id(), githubUser.username(), githubUser.email());
        newUser.getRoles().add(role);
        User savedUser = userRepository.save(newUser);
        logger.info("Nuevo usuario '{}' creado con ID {} y rol {}.", savedUser.getGithubUsername(), savedUser.getId(), roleName);
        return savedUser;
    }
}
