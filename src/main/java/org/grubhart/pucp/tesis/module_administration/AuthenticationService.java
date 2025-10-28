package org.grubhart.pucp.tesis.module_administration;

import jakarta.transaction.Transactional;
import org.grubhart.pucp.tesis.module_domain.GithubUserAuthenticator;
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
    private final GithubUserAuthenticator githubUserAuthenticator;

    public AuthenticationService(UserRepository userRepository, Environment environment, RoleRepository roleRepository, GithubUserAuthenticator githubUserAuthenticator) {
        this.userRepository = userRepository;
        this.environment = environment;
        this.roleRepository = roleRepository;
        this.githubUserAuthenticator = githubUserAuthenticator;
    }

    @Transactional
    public LoginProcessingResult processNewLogin(GithubUserDto githubUser) {
        Optional<User> userOptional = userRepository.findByGithubUsernameIgnoreCase(githubUser.username());

        // Flow for a user that already exists in the DB (possibly created by UserSyncService)
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            logger.info("User '{}' already exists in the database. Verifying roles.", githubUser.username());

            // SPECIAL CASE: The system has no admin, and this user is the designated initial admin, but doesn't have the role.
            // This happens if UserSyncService created the user before their first login.
            boolean shouldBeAdmin = isInitialBootstrap() && user.getGithubUsername().equalsIgnoreCase(environment.getProperty("dora.initial-admin-username"));
            boolean isAlreadyAdmin = user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN);

            if (shouldBeAdmin && !isAlreadyAdmin) {
                logger.info("User '{}' is the initial administrator but does not have the role. Assigning ADMIN role.", user.getGithubUsername());
                Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                        .orElseThrow(() -> new IllegalStateException("The ADMIN role was not found in the database."));
                user.getRoles().add(adminRole);
                userRepository.save(user);
                return new LoginProcessingResult(user, true); // This is the first administrator
            }

            return new LoginProcessingResult(user, false); // Existing user, not the first admin on this login.
        }

        // If the user does not exist, we decide which creation flow to follow.
        if (isInitialBootstrap()) {
            logger.debug("System in initial bootstrap mode. Delegating to handleInitialBootstrap.");
            User bootstrappedUser = handleInitialBootstrap(githubUser);
            boolean isFirstAdmin = bootstrappedUser.getRoles().stream()
                    .anyMatch(role -> role.getName() == RoleName.ADMIN);
            return new LoginProcessingResult(bootstrappedUser, isFirstAdmin);
        } else {
            logger.debug("System already initialized. Delegating to handleRegularLogin.");
            User regularUser = handleRegularLogin(githubUser);
            return new LoginProcessingResult(regularUser, false); // A new user in normal mode is never the "first administrator".
        }
    }

    private boolean isInitialBootstrap() {
        return !userRepository.existsByRoles_Name(RoleName.ADMIN);
    }

    private User handleInitialBootstrap(GithubUserDto githubUser) {
        logger.debug("Inside handleInitialBootstrap for user '{}'", githubUser.username());
        String initialAdminUsername = environment.getProperty("dora.initial-admin-username");
        logger.debug("Value of 'dora.initial-admin-username': '{}'", initialAdminUsername);

        if (initialAdminUsername == null || initialAdminUsername.trim().isEmpty()) {
            logger.error("The environment variable 'dora.initial-admin-username' is not configured or is empty.");
            throw new IllegalStateException("La configuración del administrador inicial (dora.initial-admin-username) no está definida");
        }

        String organizationName = environment.getProperty("dora.github.organization-name");
        logger.debug("Value of 'dora.github.organization-name': '{}'", organizationName);
        boolean isOrganizationDefined = organizationName != null && !organizationName.isBlank();

        boolean isInitialAdmin = githubUser.username().equalsIgnoreCase(initialAdminUsername);

        // ACCESS CONTROL: The only condition to deny access is this.
        if (!isOrganizationDefined && !isInitialAdmin) {
            logger.warn("Access denied for '{}'. The system has no organization configured and the user is not the initial administrator.", githubUser.username());
            throw new AccessDeniedException("Access denied: The system is not configured with an organization and you are not the initial administrator.");
        }

        // ROLE ASSIGNMENT: If we pass the control, we assign the appropriate role.
        if (isInitialAdmin) {
            logger.info("User '{}' matches the initial administrator. Assigning ADMIN role.", githubUser.username());
            return createUserWithRole(githubUser, RoleName.ADMIN);
        } else {
            logger.info("User '{}' does NOT match the initial administrator. Assigning default role DEVELOPER.", githubUser.username());
            return createNewUserWithDefaultRole(githubUser);
        }
    }

    private User handleRegularLogin(GithubUserDto githubUser) {
        logger.debug("Inside handleRegularLogin for user '{}'", githubUser.username());
        String organizationName = environment.getProperty("dora.github.organization-name");

        // ACCESS CONTROL: In normal mode, the organization MUST be defined.
        if (organizationName == null || organizationName.trim().isEmpty()) {
            logger.error("Access denied. The system is in normal operation mode but does not have a configured organization.");
            throw new AccessDeniedException("Access Denied. New users cannot be created because the organization is not defined.");
        }

        // MEMBERSHIP CONTROL: The user must be a member of the organization.
        boolean isMember = githubUserAuthenticator.isUserMemberOfOrganization(githubUser.username(), organizationName);
        if (!isMember) {
            logger.warn("Access denied: User '{}' is not a member of the organization '{}'.", githubUser.username(), organizationName);
            throw new AccessDeniedException("Access Denied. User is not a member of the required organization.");
        }

        // If it passes the validations, the user is created.
        logger.info("User '{}' is a member of the organization. Creating new user with default role.", githubUser.username());
        return createNewUserWithDefaultRole(githubUser);
    }

    private User createNewUserWithDefaultRole(GithubUserDto githubUser) {
        return createUserWithRole(githubUser, RoleName.DEVELOPER);
    }

    private User createUserWithRole(GithubUserDto githubUser, RoleName roleName) {
        logger.debug("Creating new user '{}' with role {}.", githubUser.username(), roleName);
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("El rol " + roleName + " no se encontró en la base de datos"));

        User newUser = new User(githubUser.id(), githubUser.username(), githubUser.email());
        newUser.getRoles().add(role);
        User savedUser = userRepository.save(newUser);
        logger.info("New user '{}' created with ID {} and role {}.", savedUser.getGithubUsername(), savedUser.getId(), roleName);
        return savedUser;
    }
}
