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

import java.util.Optional;

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
        // Primero, verificar si el usuario ya existe. Si es así, no se necesita más lógica.
        Optional<User> userOptional = userRepository.findByGithubUsernameIgnoreCase(githubUser.username());
        if (userOptional.isPresent()) {
            logger.info("Usuario '{}' ya existe en la base de datos. Devolviendo usuario existente.", githubUser.username());
            return new LoginProcessingResult(userOptional.get(), false); // Un usuario existente nunca es el "primer administrador".
        }

        // Si el usuario no existe, decidimos qué flujo de creación seguir.
        if (isInitialBootstrap()) {
            logger.debug("Sistema en modo de arranque inicial. Delegando a handleInitialBootstrap.");
            User bootstrappedUser = handleInitialBootstrap(githubUser);
            boolean isFirstAdmin = bootstrappedUser.getRoles().stream()
                    .anyMatch(role -> role.getName() == RoleName.ADMIN);
            return new LoginProcessingResult(bootstrappedUser, isFirstAdmin);
        } else {
            logger.debug("Sistema ya inicializado. Delegando a handleRegularLogin.");
            User regularUser = handleRegularLogin(githubUser);
            return new LoginProcessingResult(regularUser, false); // Un usuario nuevo en modo normal nunca es el "primer administrador".
        }
    }

    private boolean isInitialBootstrap() {
        return !userRepository.existsByRoles_Name(RoleName.ADMIN);
    }

    private User handleInitialBootstrap(GithubUserDto githubUser) {
        logger.debug("Dentro de handleInitialBootstrap para el usuario '{}'", githubUser.username());
        String initialAdminUsername = environment.getProperty("dora.initial-admin-username");
        logger.debug("Valor de 'dora.initial-admin-username': '{}'", initialAdminUsername);

        if (initialAdminUsername == null || initialAdminUsername.trim().isEmpty()) {
            logger.error("La variable de entorno 'dora.initial-admin-username' no está configurada o está vacía.");
            throw new IllegalStateException("La configuración del administrador inicial (dora.initial-admin-username) no está definida. El sistema no puede arrancar de forma segura.");
        }

        String organizationName = environment.getProperty("dora.github.organization-name");
        logger.debug("Valor de 'dora.github.organization-name': '{}'", organizationName);
        boolean isOrganizationDefined = organizationName != null && !organizationName.isBlank();

        boolean isInitialAdmin = githubUser.username().equalsIgnoreCase(initialAdminUsername);

        // CONTROL DE ACCESO: La única condición para denegar el acceso es esta.
        if (!isOrganizationDefined && !isInitialAdmin) {
            logger.warn("Acceso denegado para '{}'. El sistema no tiene organización configurada y el usuario no es el administrador inicial.", githubUser.username());
            throw new AccessDeniedException("Access denied: The system is not configured with an organization and you are not the initial administrator.");
        }

        // ASIGNACIÓN DE ROL: Si pasamos el control, asignamos el rol apropiado.
        if (isInitialAdmin) {
            logger.info("El usuario '{}' coincide con el administrador inicial. Asignando rol ADMIN.", githubUser.username());
            return createUserWithRole(githubUser, RoleName.ADMIN);
        } else {
            logger.info("El usuario '{}' NO coincide con el administrador inicial. Asignando rol por defecto DEVELOPER.", githubUser.username());
            return createNewUserWithDefaultRole(githubUser);
        }
    }

    private User handleRegularLogin(GithubUserDto githubUser) {
        logger.debug("Dentro de handleRegularLogin para el usuario '{}'", githubUser.username());
        String organizationName = environment.getProperty("dora.github.organization-name");

        // CONTROL DE ACCESO: En modo normal, la organización DEBE estar definida.
        if (organizationName == null || organizationName.trim().isEmpty()) {
            logger.error("Acceso denegado. El sistema está en modo de operación normal pero no tiene una organización configurada.");
            throw new AccessDeniedException("Access Denied. New users cannot be created because the organization is not defined.");
        }

        // CONTROL DE MEMBRESÍA: El usuario debe ser miembro de la organización.
        boolean isMember = githubClient.isUserMemberOfOrganization(githubUser.username(), organizationName);
        if (!isMember) {
            logger.warn("Acceso denegado: El usuario '{}' no es miembro de la organización '{}'.", githubUser.username(), organizationName);
            throw new AccessDeniedException("Access Denied. User is not a member of the required organization.");
        }

        // Si pasa las validaciones, se crea el usuario.
        logger.info("Usuario '{}' es miembro de la organización. Creando nuevo usuario con rol por defecto.", githubUser.username());
        return createNewUserWithDefaultRole(githubUser);
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