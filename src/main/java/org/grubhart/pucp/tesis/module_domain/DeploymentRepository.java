package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Optional<Deployment> findByGithubId(Long githubId);

    List<Deployment> findByLeadTimeProcessedFalse(Sort sort);

    List<Deployment> findByLeadTimeProcessedFalseAndEnvironment(String environment, Sort sort);

    Optional<Deployment> findFirstByEnvironmentAndCreatedAtBefore(String environment, LocalDateTime createdAt, Sort sort);

}
