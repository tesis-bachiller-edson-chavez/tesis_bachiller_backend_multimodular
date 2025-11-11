package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByRoles_Name(RoleName roleName);

    Optional<User> findByGithubUsernameIgnoreCase(String username);

    List<User> findAllByActiveTrue();

    /**
     * Find all users that belong to a specific team
     */
    List<User> findByTeamId(Long teamId);

    /**
     * Count users in a specific team
     */
    long countByTeamId(Long teamId);

    /**
     * Find all users that belong to a specific team and have a specific role
     */
    List<User> findByTeamIdAndRoles_Name(Long teamId, RoleName roleName);

    /**
     * Count users in a specific team with a specific role
     */
    long countByTeamIdAndRoles_Name(Long teamId, RoleName roleName);

}
