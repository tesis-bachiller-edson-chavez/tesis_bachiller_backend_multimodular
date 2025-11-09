package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByDatadogIncidentId(String datadogIncidentId);

    List<Incident> findByServiceNameAndStateAndStartTimeBetween(
            String serviceName,
            IncidentState state,
            LocalDateTime start,
            LocalDateTime end
    );

    long countByServiceNameAndStartTimeBetween(
            String serviceName,
            LocalDateTime start,
            LocalDateTime end
    );
}
