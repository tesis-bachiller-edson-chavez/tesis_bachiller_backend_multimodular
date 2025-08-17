package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByRoles_Name(RoleName roleName);

    Optional<User> findByGithubUsernameIgnoreCase(String username);

}
