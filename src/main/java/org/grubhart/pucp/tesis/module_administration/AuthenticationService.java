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
        this.roleRepository=roleRepository;
        this.githubClient = githubClient;
    }

    @Transactional
    public LoginProcessingResult processNewLogin(GithubUserDto githubUser) {
        logger.info("Procesando login para el usuario de GitHub: {}", githubUser.username());

        // AC 1.3: Verificar pertenencia a la organización ANTES de cualquier otra cosa,
        // excepto para el flujo de bootstrap del primer admin.
        String organizationName = environment.getProperty("dora.github.organization-name");
        if (organizationName != null && !organizationName.isBlank()) {
            // Solo verificamos si la organización está configurada y no estamos en el flujo de bootstrap
            if (userRepository.existsByRoles_Name(RoleName.ADMIN)) {
                if (!githubClient.isUserMemberOfOrganization(githubUser.username(), organizationName)) {
                    throw new AccessDeniedException("Acceso denegado: El usuario '" + githubUser.username() + "' no es miembro de la organización '" + organizationName + "'.");
                }
            }
        }

        // Si no hay administrador, manejamos el caso especial de arranque inicial.
        if (!userRepository.existsByRoles_Name(RoleName.ADMIN)) {
            logger.debug("No se encontró ningún administrador. Ejecutando lógica de arranque inicial.");
            User bootstrappedUser = handleInitialBootstrap(githubUser);
            // El usuario es el "primer admin" si se le asignó el rol de ADMIN durante el bootstrap.
            boolean isFirstAdmin = bootstrappedUser.getRoles().stream()
                    .anyMatch(role -> role.getName() == RoleName.ADMIN);
            return new LoginProcessingResult(bootstrappedUser, isFirstAdmin);
        }
        
        logger.debug("Sistema ya inicializado. Procesando login normal.");
        // Si el sistema ya está configurado, procesamos un login normal.
        User user = userRepository.findByGithubUsernameIgnoreCase(githubUser.username())
                .map(existingUser -> {
                    logger.info("Usuario '{}' ya existe en la base de datos. Devolviendo usuario existente.", existingUser.getGithubUsername());
                    return existingUser;
                }).orElseGet(() -> {
                    logger.info("Usuario '{}' no encontrado. Creando nuevo usuario con rol por defecto.", githubUser.username());
                    return createNewUserWithDefaultRole(githubUser);
                });
        return new LoginProcessingResult(user, false);
    }

    /**
     * Maneja la lógica de creación del primer usuario en el sistema.
     * Asigna el rol de ADMIN o DEVELOPER según la configuración.
     */
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
            Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow(() -> new IllegalStateException("El rol ADMIN no se encontró en la base de datos. La inicialización falló."));
            User adminUser = new User(githubUser.id(), githubUser.username(), githubUser.email());
            adminUser.getRoles().add(adminRole);
            logger.debug("grabando usuario admin en base de datos", adminUser);
            return userRepository.save(adminUser);
        } else {
            logger.info("El usuario '{}' NO coincide con el administrador inicial. Asignando rol por defecto DEVELOPER.", githubUser.username());
            return createNewUserWithDefaultRole(githubUser);
        }
    }

    /**
     * Crea un nuevo usuario, le asigna el rol por defecto DEVELOPER y lo guarda.
     */
    private User createNewUserWithDefaultRole(GithubUserDto githubUser) {
        logger.debug("Creando nuevo usuario '{}' con rol DEVELOPER.", githubUser.username());
        Role developerRole = roleRepository.findByName(RoleName.DEVELOPER)
                .orElseThrow(() -> new IllegalStateException("El rol DEVELOPER no se encontró en la base de datos. La inicialización falló."));

        User newUser = new User(githubUser.id(), githubUser.username(), githubUser.email());
        newUser.getRoles().add(developerRole);
        User savedUser = userRepository.save(newUser);
        logger.info("Nuevo usuario '{}' creado con ID {} y rol DEVELOPER.", savedUser.getGithubUsername(), savedUser.getId());
        return savedUser;
    }
}
