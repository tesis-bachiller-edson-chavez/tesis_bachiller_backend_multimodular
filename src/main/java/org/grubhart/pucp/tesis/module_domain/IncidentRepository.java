package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findByDatadogIncidentId(String datadogIncidentId);

    /**
     * Find incidents that affect a specific service, have a given state, and fall within a time range.
     */
    @Query("SELECT i FROM Incident i WHERE :serviceName MEMBER OF i.serviceNames " +
           "AND i.state = :state AND i.startTime BETWEEN :start AND :end")
    List<Incident> findByServiceNameAndStateAndStartTimeBetween(
            @Param("serviceName") String serviceName,
            @Param("state") IncidentState state,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    /**
     * Count incidents that affect a specific service within a time range.
     */
    @Query("SELECT COUNT(i) FROM Incident i WHERE :serviceName MEMBER OF i.serviceNames " +
           "AND i.startTime BETWEEN :start AND :end")
    long countByServiceNameAndStartTimeBetween(
            @Param("serviceName") String serviceName,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}
