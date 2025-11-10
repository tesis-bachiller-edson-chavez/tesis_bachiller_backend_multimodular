package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_collector.datadog.DatadogIncidentClient;
import org.grubhart.pucp.tesis.module_collector.datadog.dto.*;
import org.grubhart.pucp.tesis.module_domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentSyncServiceTest {

    @Mock
    private DatadogIncidentClient datadogClient;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private SyncStatusRepository syncStatusRepository;

    @Mock
    private RepositoryConfigRepository repositoryConfigRepository;

    @InjectMocks
    private IncidentSyncService incidentSyncService;

    @Captor
    private ArgumentCaptor<Incident> incidentCaptor;

    @Captor
    private ArgumentCaptor<SyncStatus> syncStatusCaptor;

    private static final String SERVICE_NAME = "tesis-backend";
    private static final String JOB_NAME_PREFIX = "DATADOG_INCIDENT_SYNC_";

    @BeforeEach
    void setUp() {
        incidentSyncService = new IncidentSyncService(
                datadogClient,
                incidentRepository,
                syncStatusRepository,
                repositoryConfigRepository
        );
    }

    @Test
    @DisplayName("GIVEN no configured repositories WHEN syncing incidents THEN should do nothing and log a warning")
    void shouldDoNothingWhenNoRepositoriesConfigured() {
        // Given
        when(repositoryConfigRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        incidentSyncService.syncIncidents();

        // Then
        verify(datadogClient, never()).getIncidents(any(), any());
        verify(incidentRepository, never()).save(any());
        verify(syncStatusRepository, never()).save(any());
    }

    @Test
    @DisplayName("GIVEN a repository with a null service name WHEN syncing THEN should skip that repository")
    void shouldSkipServiceWhenServiceNameIsNull() {
        // Given
        RepositoryConfig nullServiceConfig = new RepositoryConfig("https://github.com/test/repo-null", null);
        RepositoryConfig validServiceConfig = new RepositoryConfig("https://github.com/test/repo-valid", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(nullServiceConfig, validServiceConfig));

        DatadogIncidentResponse emptyResponse = new DatadogIncidentResponse(Collections.emptyList(), new DatadogMeta(new DatadogPagination(0, 0)));
        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(emptyResponse);

        // When
        incidentSyncService.syncIncidents();

        // Then
        verify(datadogClient, times(1)).getIncidents(any(Instant.class), eq(SERVICE_NAME));
        verify(datadogClient, never()).getIncidents(any(Instant.class), isNull());
        verify(syncStatusRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("GIVEN a repository with a blank service name WHEN syncing THEN should skip that repository")
    void shouldSkipServiceWhenServiceNameIsBlank() {
        // Given
        RepositoryConfig blankServiceConfig = new RepositoryConfig("https://github.com/test/repo-blank", "  ");
        RepositoryConfig validServiceConfig = new RepositoryConfig("https://github.com/test/repo-valid", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(blankServiceConfig, validServiceConfig));

        DatadogIncidentResponse emptyResponse = new DatadogIncidentResponse(Collections.emptyList(), new DatadogMeta(new DatadogPagination(0, 0)));
        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(emptyResponse);

        // When
        incidentSyncService.syncIncidents();

        // Then
        verify(datadogClient, times(1)).getIncidents(any(Instant.class), eq(SERVICE_NAME));
        verify(datadogClient, never()).getIncidents(any(Instant.class), eq("  "));
        verify(syncStatusRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("GIVEN an exception during sync for one service WHEN syncing THEN should continue with other services")
    void shouldContinueSyncWhenOneServiceThrowsException() {
        // Given
        String failingService = "failing-service";
        String workingService = "working-service";
        RepositoryConfig failingConfig = new RepositoryConfig("https://github.com/test/repo-fail", failingService);
        RepositoryConfig workingConfig = new RepositoryConfig("https://github.com/test/repo-work", workingService);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(failingConfig, workingConfig));

        when(datadogClient.getIncidents(any(Instant.class), eq(failingService)))
                .thenThrow(new RuntimeException("API connection failed"));

        DatadogIncidentResponse emptyResponse = new DatadogIncidentResponse(Collections.emptyList(), new DatadogMeta(new DatadogPagination(0, 0)));
        when(datadogClient.getIncidents(any(Instant.class), eq(workingService))).thenReturn(emptyResponse);

        // When
        incidentSyncService.syncIncidents();

        // Then
        verify(datadogClient).getIncidents(any(Instant.class), eq(failingService));
        verify(datadogClient).getIncidents(any(Instant.class), eq(workingService));
        verify(syncStatusRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("GIVEN multiple repositories WHEN syncing THEN should sync for all")
    void shouldSyncForAllConfiguredRepositories() {
        // GIVEN
        RepositoryConfig repo1 = new RepositoryConfig("https://github.com/owner1/repo1", "service1");
        RepositoryConfig repo2 = new RepositoryConfig("https://github.com/owner2/repo2", "service2");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo1, repo2));

        DatadogIncidentResponse emptyResponse = new DatadogIncidentResponse(Collections.emptyList(), new DatadogMeta(new DatadogPagination(0, 0)));
        when(datadogClient.getIncidents(any(Instant.class), anyString())).thenReturn(emptyResponse);

        // WHEN
        incidentSyncService.syncIncidents();

        // THEN
        verify(datadogClient, times(1)).getIncidents(any(), eq("service1"));
        verify(datadogClient, times(1)).getIncidents(any(), eq("service2"));
        verify(syncStatusRepository, times(2)).save(any());
    }


    @Test
    @DisplayName("GIVEN no previous sync WHEN syncing incidents THEN should fetch incidents from epoch time")
    void shouldFetchIncidentsFromEpochWhenNoPreviousSync() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(JOB_NAME_PREFIX + SERVICE_NAME)).thenReturn(Optional.empty());
        DatadogIncidentResponse emptyResponse = new DatadogIncidentResponse(
                Collections.emptyList(),
                new DatadogMeta(new DatadogPagination(0, 0))
        );
        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(emptyResponse);

        // When
        incidentSyncService.syncIncidents();

        // Then
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(datadogClient).getIncidents(instantCaptor.capture(), eq(SERVICE_NAME));

        // Verify it's fetching from a reasonable past time (e.g., 30 days ago)
        Instant capturedInstant = instantCaptor.getValue();
        assertThat(capturedInstant).isBefore(Instant.now());
    }

    @Test
    @DisplayName("GIVEN previous sync exists WHEN syncing incidents THEN should fetch incidents since last sync")
    void shouldFetchIncidentsSinceLastSync() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        LocalDateTime lastSync = LocalDateTime.of(2025, 1, 1, 12, 0);
        SyncStatus syncStatus = new SyncStatus(JOB_NAME_PREFIX + SERVICE_NAME, lastSync);
        when(syncStatusRepository.findById(JOB_NAME_PREFIX + SERVICE_NAME)).thenReturn(Optional.of(syncStatus));

        DatadogIncidentResponse emptyResponse = new DatadogIncidentResponse(
                Collections.emptyList(),
                new DatadogMeta(new DatadogPagination(0, 0))
        );
        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(emptyResponse);

        // When
        incidentSyncService.syncIncidents();

        // Then
        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(datadogClient).getIncidents(instantCaptor.capture(), eq(SERVICE_NAME));

        Instant expectedInstant = lastSync.toInstant(ZoneOffset.UTC);
        assertThat(instantCaptor.getValue()).isEqualTo(expectedInstant);
    }

    @Test
    @DisplayName("GIVEN new incidents from Datadog WHEN syncing THEN should save all new incidents")
    void shouldSaveAllNewIncidents() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        Instant createdTime = Instant.parse("2025-01-01T12:00:00Z");
        Instant resolvedTime = Instant.parse("2025-01-01T14:00:00Z");

        DatadogIncidentResponse response = new DatadogIncidentResponse(
                List.of(
                        new DatadogIncidentData(
                                "incident-123",
                                "incidents",
                                new DatadogIncidentAttributes(
                                        "Database connection timeout",
                                        "all",
                                        createdTime,
                                        resolvedTime,
                                        resolvedTime,
                                        "resolved",
                                        "SEV-2",
                                        new DatadogIncidentFields(
                                                new DatadogFieldValue("resolved"),
                                                new DatadogFieldValue("SEV-2")
                                        )
                                )
                        )
                ),
                new DatadogMeta(new DatadogPagination(0, 1))
        );

        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(response);
        when(incidentRepository.findByDatadogIncidentId("incident-123")).thenReturn(Optional.empty());

        // When
        incidentSyncService.syncIncidents();

        // Then
        verify(incidentRepository).save(incidentCaptor.capture());

        Incident savedIncident = incidentCaptor.getValue();
        assertThat(savedIncident.getDatadogIncidentId()).isEqualTo("incident-123");
        assertThat(savedIncident.getTitle()).isEqualTo("Database connection timeout");
        assertThat(savedIncident.getState()).isEqualTo(IncidentState.RESOLVED);
        assertThat(savedIncident.getSeverity()).isEqualTo(IncidentSeverity.SEV2);
        assertThat(savedIncident.getServiceName()).isEqualTo(SERVICE_NAME);
        assertThat(savedIncident.getDurationSeconds()).isEqualTo(7200L); // 2 hours
    }

    @Test
    @DisplayName("GIVEN existing incident WHEN syncing with updates THEN should update the incident")
    void shouldUpdateExistingIncident() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        Instant createdTime = Instant.parse("2025-01-01T12:00:00Z");
        Instant resolvedTime = Instant.parse("2025-01-01T14:00:00Z");

        Incident existingIncident = new Incident(
                "incident-123",
                "Database connection timeout",
                IncidentState.ACTIVE,
                IncidentSeverity.SEV2,
                LocalDateTime.ofInstant(createdTime, ZoneOffset.UTC),
                null,
                null,
                SERVICE_NAME,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        existingIncident.setId(1L);

        DatadogIncidentResponse response = new DatadogIncidentResponse(
                List.of(
                        new DatadogIncidentData(
                                "incident-123",
                                "incidents",
                                new DatadogIncidentAttributes(
                                        "Database connection timeout",
                                        "all",
                                        createdTime,
                                        resolvedTime,
                                        resolvedTime,
                                        "resolved",
                                        "SEV-2",
                                        new DatadogIncidentFields(
                                                new DatadogFieldValue("resolved"),
                                                new DatadogFieldValue("SEV-2")
                                        )
                                )
                        )
                ),
                new DatadogMeta(new DatadogPagination(0, 1))
        );

        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(response);
        when(incidentRepository.findByDatadogIncidentId("incident-123")).thenReturn(Optional.of(existingIncident));

        // When
        incidentSyncService.syncIncidents();

        // Then
        verify(incidentRepository).save(incidentCaptor.capture());

        Incident updatedIncident = incidentCaptor.getValue();
        assertThat(updatedIncident.getId()).isEqualTo(1L); // Same ID - it's an update
        assertThat(updatedIncident.getState()).isEqualTo(IncidentState.RESOLVED); // Updated state
        assertThat(updatedIncident.getResolvedTime()).isNotNull(); // Now has resolved time
        assertThat(updatedIncident.getDurationSeconds()).isEqualTo(7200L); // Now has duration
    }

    @Test
    @DisplayName("GIVEN active incident with no resolution WHEN syncing THEN should save without duration")
    void shouldHandleActiveIncidentWithoutResolution() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        Instant createdTime = Instant.parse("2025-01-01T12:00:00Z");

        DatadogIncidentResponse response = new DatadogIncidentResponse(
                List.of(
                        new DatadogIncidentData(
                                "incident-456",
                                "incidents",
                                new DatadogIncidentAttributes(
                                        "Ongoing issue",
                                        "partial",
                                        createdTime,
                                        createdTime,
                                        null, // No resolution yet
                                        "active",
                                        "SEV-1",
                                        new DatadogIncidentFields(
                                                new DatadogFieldValue("active"),
                                                new DatadogFieldValue("SEV-1")
                                        )
                                )
                        )
                ),
                new DatadogMeta(new DatadogPagination(0, 1))
        );

        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(response);
        when(incidentRepository.findByDatadogIncidentId("incident-456")).thenReturn(Optional.empty());

        // When
        incidentSyncService.syncIncidents();

        // Then
        verify(incidentRepository).save(incidentCaptor.capture());

        Incident savedIncident = incidentCaptor.getValue();
        assertThat(savedIncident.getState()).isEqualTo(IncidentState.ACTIVE);
        assertThat(savedIncident.getResolvedTime()).isNull();
        assertThat(savedIncident.getDurationSeconds()).isNull();
    }

    @Test
    @DisplayName("GIVEN successful sync WHEN completing THEN should update sync status")
    void shouldUpdateSyncStatusAfterSuccessfulSync() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
        DatadogIncidentResponse emptyResponse = new DatadogIncidentResponse(
                Collections.emptyList(),
                new DatadogMeta(new DatadogPagination(0, 0))
        );
        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(emptyResponse);

        // When
        LocalDateTime beforeSync = LocalDateTime.now();
        incidentSyncService.syncIncidents();
        LocalDateTime afterSync = LocalDateTime.now();

        // Then
        verify(syncStatusRepository).save(syncStatusCaptor.capture());

        SyncStatus savedStatus = syncStatusCaptor.getValue();
        assertThat(savedStatus.getJobName()).isEqualTo(JOB_NAME_PREFIX + SERVICE_NAME);
        assertThat(savedStatus.getLastSuccessfulRun()).isBetween(beforeSync, afterSync);
    }

    @Test
    @DisplayName("GIVEN empty response from Datadog WHEN syncing THEN should not save any incidents")
    void shouldHandleEmptyResponseGracefully() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
        DatadogIncidentResponse emptyResponse = new DatadogIncidentResponse(
                Collections.emptyList(),
                new DatadogMeta(new DatadogPagination(0, 0))
        );
        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(emptyResponse);

        // When
        incidentSyncService.syncIncidents();

        // Then
        verify(incidentRepository, never()).save(any(Incident.class));
        verify(syncStatusRepository).save(any(SyncStatus.class)); // Still updates sync status
    }

    @Test
    @DisplayName("GIVEN an error processing one incident WHEN syncing THEN should continue with next incidents")
    void shouldContinueProcessingWhenSingleIncidentFails() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        Instant now = Instant.now();
        DatadogIncidentData failingIncidentData = new DatadogIncidentData(
                "incident-1", "incidents", new DatadogIncidentAttributes("Failing", "none", now, now, null, "active", "SEV-5", null)
        );
        DatadogIncidentData successfulIncidentData = new DatadogIncidentData(
                "incident-2", "incidents", new DatadogIncidentAttributes("Successful", "none", now, now, null, "active", "SEV-5", null)
        );

        DatadogIncidentResponse response = new DatadogIncidentResponse(
                List.of(failingIncidentData, successfulIncidentData),
                new DatadogMeta(new DatadogPagination(0, 2))
        );

        when(datadogClient.getIncidents(any(Instant.class), eq(SERVICE_NAME))).thenReturn(response);
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        // Make the first incident fail on save
        doThrow(new RuntimeException("Database constraint violation"))
                .when(incidentRepository).save(argThat(incident -> incident.getDatadogIncidentId().equals("incident-1")));

        // When
        incidentSyncService.syncIncidents();

        // Then
        // Verify that save was attempted for both incidents
        verify(incidentRepository, times(2)).save(incidentCaptor.capture());

        List<Incident> capturedIncidents = incidentCaptor.getAllValues();
        assertThat(capturedIncidents).hasSize(2);
        assertThat(capturedIncidents.get(0).getDatadogIncidentId()).isEqualTo("incident-1"); // First attempt (failed)
        assertThat(capturedIncidents.get(1).getDatadogIncidentId()).isEqualTo("incident-2"); // Second attempt (succeeded)

        // Verify that the sync status is still updated at the end
        verify(syncStatusRepository).save(any(SyncStatus.class));
    }

    @ParameterizedTest
    @CsvSource({
            "resolved, RESOLVED",
            "stable, STABLE",
            "active, ACTIVE",
            "unknown, ACTIVE",
            ", ACTIVE"
    })
    @DisplayName("GIVEN various string states WHEN mapping THEN should return correct IncidentState enum")
    void shouldMapStateCorrectly(String inputState, IncidentState expectedState) {
        // When
        IncidentState actualState = incidentSyncService.mapState(inputState);

        // Then
        assertThat(actualState).isEqualTo(expectedState);
    }

    @ParameterizedTest
    @CsvSource({
            "SEV-1, SEV1",
            "SEV-2, SEV2",
            "SEV-3, SEV3",
            "SEV-4, SEV4",
            "SEV-5, SEV5",
            "UNKNOWN, SEV5",
            ", SEV5"
    })
    @DisplayName("GIVEN various string severities WHEN mapping THEN should return correct IncidentSeverity enum")
    void shouldMapSeverityCorrectly(String inputSeverity, IncidentSeverity expectedSeverity) {
        // When
        IncidentSeverity actualSeverity = incidentSyncService.mapSeverity(inputSeverity);

        // Then
        assertThat(actualSeverity).isEqualTo(expectedSeverity);
    }

    @Test
    @DisplayName("GIVEN incident with null modified date WHEN mapping THEN should use created date for updated_at")
    void shouldUseCreatedDateForUpdatedAtWhenModifiedDateIsNull() {
        // Given
        Instant createdTime = Instant.parse("2025-02-01T10:00:00Z");
        DatadogIncidentData incidentData = new DatadogIncidentData(
                "incident-null-modified",
                "incidents",
                new DatadogIncidentAttributes(
                        "Title", "none", createdTime, null, null, "active", "SEV-3", null
                )
        );

        // When
        Incident result = incidentSyncService.mapToIncident(incidentData, SERVICE_NAME);

        // Then
        assertThat(result.getUpdatedAt()).isEqualTo(result.getCreatedAt());
        assertThat(result.getUpdatedAt()).isEqualTo(LocalDateTime.ofInstant(createdTime, ZoneOffset.UTC));
    }
}
