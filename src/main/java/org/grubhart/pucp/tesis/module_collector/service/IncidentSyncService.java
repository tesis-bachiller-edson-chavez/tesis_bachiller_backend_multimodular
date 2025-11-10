package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_collector.datadog.DatadogIncidentClient;
import org.grubhart.pucp.tesis.module_collector.datadog.dto.DatadogIncidentData;
import org.grubhart.pucp.tesis.module_collector.datadog.dto.DatadogIncidentResponse;
import org.grubhart.pucp.tesis.module_domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class IncidentSyncService {

    private static final Logger log = LoggerFactory.getLogger(IncidentSyncService.class);
    private static final String JOB_NAME_PREFIX = "DATADOG_INCIDENT_SYNC_";

    private final DatadogIncidentClient datadogClient;
    private final IncidentRepository incidentRepository;
    private final SyncStatusRepository syncStatusRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;

    public IncidentSyncService(
            DatadogIncidentClient datadogClient,
            IncidentRepository incidentRepository,
            SyncStatusRepository syncStatusRepository,
            RepositoryConfigRepository repositoryConfigRepository) {
        this.datadogClient = datadogClient;
        this.incidentRepository = incidentRepository;
        this.syncStatusRepository = syncStatusRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
    }

    @Scheduled(initialDelay = 40000, fixedRate = 3600000) // Initial delay: 40s, then every 60 minutes
    public void syncIncidents() {
        log.info("Starting Datadog incident synchronization for all configured repositories");

        var repositories = repositoryConfigRepository.findAll();
        if (repositories.isEmpty()) {
            log.warn("No repositories configured for incident synchronization");
            return;
        }

        int totalCreated = 0;
        int totalUpdated = 0;

        for (RepositoryConfig repository : repositories) {
            String serviceName = repository.getDatadogServiceName();

            if (serviceName == null || serviceName.isBlank()) {
                log.debug("Skipping repository {} - no Datadog service name configured",
                         repository.getRepositoryUrl());
                continue;
            }

            String jobName = JOB_NAME_PREFIX + serviceName;

            try {
                log.info("Syncing incidents for service: {} (repository: {})",
                        serviceName, repository.getRepositoryUrl());

                Instant since = getLastSyncTimestamp(jobName);
                DatadogIncidentResponse response = datadogClient.getIncidents(since, serviceName);

                int created = 0;
                int updated = 0;

                for (DatadogIncidentData incidentData : response.data()) {
                    try {
                        Incident incident = mapToIncident(incidentData, repository);
                        Optional<Incident> existing = incidentRepository.findByDatadogIncidentId(incident.getDatadogIncidentId());

                        if (existing.isPresent()) {
                            updateIncident(existing.get(), incident);
                            updated++;
                        } else {
                            incidentRepository.save(incident);
                            created++;
                        }
                    } catch (Exception e) {
                        log.error("Error processing incident {}: {}", incidentData.id(), e.getMessage(), e);
                    }
                }

                updateSyncStatus(jobName);
                totalCreated += created;
                totalUpdated += updated;
                log.info("Service {} sync completed: {} created, {} updated", serviceName, created, updated);

            } catch (Exception e) {
                log.error("Error syncing incidents for service {}: {}", serviceName, e.getMessage(), e);
            }
        }
        
        log.info("Incident sync completed for all services: {} total created, {} total updated",
                totalCreated, totalUpdated);
    }

    private Instant getLastSyncTimestamp(String jobName) {
        Optional<SyncStatus> syncStatus = syncStatusRepository.findById(jobName);
        if (syncStatus.isPresent()) {
            LocalDateTime lastRun = syncStatus.get().getLastSuccessfulRun();
            return lastRun.toInstant(ZoneOffset.UTC);
        } else {
            // Default: fetch incidents from last 30 days
            return Instant.now().minus(Duration.ofDays(30));
        }
    }

    Incident mapToIncident(DatadogIncidentData data, RepositoryConfig repositoryConfig) {
        LocalDateTime createdAt = LocalDateTime.ofInstant(data.attributes().created(), ZoneOffset.UTC);
        LocalDateTime updatedAt = LocalDateTime.ofInstant(
                data.attributes().modified() != null ? data.attributes().modified() : data.attributes().created(),
                ZoneOffset.UTC
        );

        LocalDateTime resolvedTime = data.attributes().resolved() != null
                ? LocalDateTime.ofInstant(data.attributes().resolved(), ZoneOffset.UTC)
                : null;

        Long durationSeconds = null;
        if (resolvedTime != null) {
            durationSeconds = Duration.between(createdAt, resolvedTime).getSeconds();
        }

        IncidentState state = mapState(data.attributes().state());
        IncidentSeverity severity = mapSeverity(data.attributes().severity());

        return new Incident(
                data.id(),
                repositoryConfig,
                data.attributes().title(),
                state,
                severity,
                createdAt,
                resolvedTime,
                durationSeconds,
                repositoryConfig.getDatadogServiceName(),
                createdAt,
                updatedAt
        );
    }

    private void updateIncident(Incident existing, Incident updated) {
        existing.setState(updated.getState());
        existing.setSeverity(updated.getSeverity());
        existing.setResolvedTime(updated.getResolvedTime());
        existing.setDurationSeconds(updated.getDurationSeconds());
        existing.setUpdatedAt(updated.getUpdatedAt());

        incidentRepository.save(existing);
    }

    private void updateSyncStatus(String jobName) {
        LocalDateTime now = LocalDateTime.now();
        SyncStatus syncStatus = new SyncStatus(jobName, now);
        syncStatusRepository.save(syncStatus);
    }

    IncidentState mapState(String state) {
        if (state == null) {
            return IncidentState.ACTIVE;
        }

        return switch (state.toLowerCase()) {
            case "resolved" -> IncidentState.RESOLVED;
            case "stable" -> IncidentState.STABLE;
            default -> IncidentState.ACTIVE;
        };
    }

    IncidentSeverity mapSeverity(String severity) {
        if (severity == null) {
            return IncidentSeverity.SEV5;
        }

        // Datadog uses formats like "SEV-1", "SEV-2", etc.
        String normalized = severity.toUpperCase().replace("-", "");

        return switch (normalized) {
            case "SEV1" -> IncidentSeverity.SEV1;
            case "SEV2" -> IncidentSeverity.SEV2;
            case "SEV3" -> IncidentSeverity.SEV3;
            case "SEV4" -> IncidentSeverity.SEV4;
            default -> IncidentSeverity.SEV5;
        };
    }
}
