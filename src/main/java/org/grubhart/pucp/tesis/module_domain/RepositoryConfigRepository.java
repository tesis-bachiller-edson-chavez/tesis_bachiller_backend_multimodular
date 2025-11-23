package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RepositoryConfigRepository extends JpaRepository<RepositoryConfig, Long> {

    /**
     * Finds all repositories that are assigned to at least one team.
     * This is used by sync services to only process repositories that are actually in use.
     */
    @Query("SELECT DISTINCT r FROM Team t JOIN t.repositories r")
    List<RepositoryConfig> findAllAssignedToTeams();
}
