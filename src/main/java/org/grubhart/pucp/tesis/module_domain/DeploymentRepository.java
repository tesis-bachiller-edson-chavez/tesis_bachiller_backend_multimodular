package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Optional<Deployment> findByGithubId(Long githubId);
}
