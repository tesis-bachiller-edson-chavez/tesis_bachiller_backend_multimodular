package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    /**
     * Find a team by its name
     */
    Optional<Team> findByName(String name);

    /**
     * Check if a team with the given name exists
     */
    boolean existsByName(String name);
}
