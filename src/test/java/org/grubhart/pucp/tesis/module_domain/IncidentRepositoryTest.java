package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class IncidentRepositoryTest {

    @Autowired
    private IncidentRepository repository;

    private LocalDateTime baseTime;

    @BeforeEach
    void setUp() {
        baseTime = LocalDateTime.of(2025, 1, 1, 12, 0);
        repository.deleteAll();
    }

    @Test
    @DisplayName("GIVEN an incident WHEN saving it THEN it should be persisted with generated ID")
    void shouldSaveAndRetrieveIncident() {
        // Given
        Incident incident = new Incident(
                "INC-123",
                null,
                "Database connection timeout",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV2,
                baseTime,
                baseTime.plusHours(2),
                7200L,
                "tesis-backend",
                baseTime,
                baseTime.plusHours(2)
        );

        // When
        Incident savedIncident = repository.save(incident);

        // Then
        assertThat(savedIncident).isNotNull();
        assertThat(savedIncident.getId()).isNotNull();
        assertThat(savedIncident.getDatadogIncidentId()).isEqualTo("INC-123");

        Incident retrievedIncident = repository.findById(savedIncident.getId()).orElse(null);
        assertThat(retrievedIncident).isNotNull();
        assertThat(retrievedIncident.getTitle()).isEqualTo("Database connection timeout");
    }

    @Test
    @DisplayName("GIVEN an incident ID from Datadog WHEN finding by datadogIncidentId THEN it should return the incident")
    void shouldFindByDatadogIncidentId() {
        // Given
        Incident incident = new Incident(
                "INC-456",
                null,
                "API rate limit exceeded",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV3,
                baseTime,
                baseTime.plusHours(1),
                3600L,
                "tesis-backend",
                baseTime,
                baseTime.plusHours(1)
        );
        repository.save(incident);

        // When
        Optional<Incident> foundIncident = repository.findByDatadogIncidentId("INC-456");

        // Then
        assertThat(foundIncident).isPresent();
        assertThat(foundIncident.get().getTitle()).isEqualTo("API rate limit exceeded");
        assertThat(foundIncident.get().getSeverity()).isEqualTo(IncidentSeverity.SEV3);
    }

    @Test
    @DisplayName("GIVEN a non-existent incident ID WHEN finding by datadogIncidentId THEN it should return empty")
    void shouldReturnEmptyWhenDatadogIncidentIdNotFound() {
        // When
        Optional<Incident> foundIncident = repository.findByDatadogIncidentId("NON-EXISTENT");

        // Then
        assertThat(foundIncident).isEmpty();
    }

    @Test
    @DisplayName("GIVEN resolved incidents in a time range WHEN finding by service, state and time range THEN it should return matching incidents")
    void shouldFindByServiceAndStateAndStartTimeBetween() {
        // Given
        Incident resolved1 = new Incident(
                "INC-100",
                null,
                "Incident 1",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV2,
                baseTime.plusHours(1),
                baseTime.plusHours(3),
                7200L,
                "tesis-backend",
                baseTime,
                baseTime.plusHours(3)
        );

        Incident resolved2 = new Incident(
                "INC-101",
                null,
                "Incident 2",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV3,
                baseTime.plusHours(5),
                baseTime.plusHours(6),
                3600L,
                "tesis-backend",
                baseTime,
                baseTime.plusHours(6)
        );

        Incident active = new Incident(
                "INC-102",
                null,
                "Incident 3",
                IncidentState.ACTIVE,
                IncidentSeverity.SEV1,
                baseTime.plusHours(2),
                null,
                null,
                "tesis-backend",
                baseTime,
                baseTime.plusHours(2)
        );

        Incident differentService = new Incident(
                "INC-103",
                null,
                "Incident 4",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV2,
                baseTime.plusHours(3),
                baseTime.plusHours(4),
                3600L,
                "other-service",
                baseTime,
                baseTime.plusHours(4)
        );

        Incident outsideRange = new Incident(
                "INC-104",
                null,
                "Incident 5",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV2,
                baseTime.plusDays(10),
                baseTime.plusDays(10).plusHours(1),
                3600L,
                "tesis-backend",
                baseTime,
                baseTime.plusDays(10).plusHours(1)
        );

        repository.save(resolved1);
        repository.save(resolved2);
        repository.save(active);
        repository.save(differentService);
        repository.save(outsideRange);

        // When
        LocalDateTime rangeStart = baseTime;
        LocalDateTime rangeEnd = baseTime.plusDays(7);
        List<Incident> foundIncidents = repository.findByServiceNameAndStateAndStartTimeBetween(
                "tesis-backend",
                IncidentState.RESOLVED,
                rangeStart,
                rangeEnd
        );

        // Then
        assertThat(foundIncidents).hasSize(2);
        assertThat(foundIncidents).extracting(Incident::getDatadogIncidentId)
                .containsExactlyInAnyOrder("INC-100", "INC-101");
    }

    @Test
    @DisplayName("GIVEN incidents in a time range WHEN counting by service and time range THEN it should return correct count")
    void shouldCountByServiceAndStartTimeBetween() {
        // Given
        Incident incident1 = new Incident(
                "INC-200",
                null,
                "Incident 1",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV2,
                baseTime.plusHours(1),
                baseTime.plusHours(2),
                3600L,
                "tesis-backend",
                baseTime,
                baseTime.plusHours(2)
        );

        Incident incident2 = new Incident(
                "INC-201",
                null,
                "Incident 2",
                IncidentState.ACTIVE,
                IncidentSeverity.SEV1,
                baseTime.plusHours(3),
                null,
                null,
                "tesis-backend",
                baseTime,
                baseTime.plusHours(3)
        );

        Incident incident3 = new Incident(
                "INC-202",
                null,
                "Incident 3",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV3,
                baseTime.plusDays(10),
                baseTime.plusDays(10).plusHours(1),
                3600L,
                "tesis-backend",
                baseTime,
                baseTime.plusDays(10).plusHours(1)
        );

        Incident differentService = new Incident(
                "INC-203",
                null,
                "Incident 4",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV2,
                baseTime.plusHours(2),
                baseTime.plusHours(3),
                3600L,
                "other-service",
                baseTime,
                baseTime.plusHours(3)
        );

        repository.save(incident1);
        repository.save(incident2);
        repository.save(incident3);
        repository.save(differentService);

        // When
        LocalDateTime rangeStart = baseTime;
        LocalDateTime rangeEnd = baseTime.plusDays(7);
        long count = repository.countByServiceNameAndStartTimeBetween(
                "tesis-backend",
                rangeStart,
                rangeEnd
        );

        // Then
        assertThat(count).isEqualTo(2); // incident1 and incident2, but not incident3 (outside range) or differentService
    }

    @Test
    @DisplayName("GIVEN unique datadogIncidentId constraint WHEN saving duplicate THEN it should fail")
    void shouldEnforceUniqueDatadogIncidentId() {
        // Given
        Incident incident1 = new Incident(
                "INC-DUPLICATE",
                null,
                "First incident",
                IncidentState.ACTIVE,
                IncidentSeverity.SEV1,
                baseTime,
                null,
                null,
                "tesis-backend",
                baseTime,
                baseTime
        );
        repository.save(incident1);

        Incident incident2 = new Incident(
                "INC-DUPLICATE",
                null,
                "Second incident with same ID",
                IncidentState.RESOLVED,
                IncidentSeverity.SEV2,
                baseTime.plusHours(1),
                baseTime.plusHours(2),
                3600L,
                "tesis-backend",
                baseTime,
                baseTime.plusHours(2)
        );

        // When & Then
        try {
            repository.saveAndFlush(incident2);
            assertThat(false).as("Should have thrown exception for duplicate datadogIncidentId").isTrue();
        } catch (Exception e) {
            // Expected - unique constraint violation
            assertThat(e).isNotNull();
        }
    }
}