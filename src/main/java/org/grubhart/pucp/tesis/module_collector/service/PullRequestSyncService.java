package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PullRequestSyncService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestSyncService.class);
    private static final String JOB_NAME_PREFIX = "PULL_REQUEST_SYNC_";

    private final PullRequestRepository pullRequestRepository;
    private final SyncStatusRepository syncStatusRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;
    private final GithubPullRequestCollector githubPullRequestCollector;

    public PullRequestSyncService(PullRequestRepository pullRequestRepository,
                                SyncStatusRepository syncStatusRepository,
                                RepositoryConfigRepository repositoryConfigRepository,
                                GithubPullRequestCollector githubPullRequestCollector) {
        this.pullRequestRepository = pullRequestRepository;
        this.syncStatusRepository = syncStatusRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
        this.githubPullRequestCollector = githubPullRequestCollector;
    }

    /**
     * Tarea programada para sincronizar Pull Requests desde los repositorios configurados.
     * Se ejecuta 20 segundos después de que la aplicación arranca y luego cada hora.
     */
    @Scheduled(initialDelay = 20000, fixedRate = 300000)
    public void syncPullRequests() {
        List<RepositoryConfig> configs = repositoryConfigRepository.findAll();
        if (configs.isEmpty()) {
            log.warn("No hay repositorios configurados para la sincronización de Pull Requests.");
            return;
        }

        log.info("Iniciando ciclo de sincronización para {} repositorios configurados.", configs.size());
        for (RepositoryConfig config : configs) {
            syncRepository(config);
        }
        log.info("Ciclo de sincronización de Pull Requests finalizado.");
    }

    private void syncRepository(RepositoryConfig config) {
        String owner = config.getOwner();
        String repo = config.getRepoName();

        if (owner == null || repo == null) {
            log.error("La URL del repositorio '{}' no tiene el formato esperado. Saltando sincronización.", config.getRepositoryUrl());
            return;
        }

        String jobName = JOB_NAME_PREFIX + owner + "/" + repo;
        Optional<SyncStatus> syncStatus = syncStatusRepository.findById(jobName);
        LocalDateTime lastSync = syncStatus.map(SyncStatus::getLastSuccessfulRun)
                .orElse(LocalDateTime.now().minusYears(1)); // Si nunca se ha sincronizado, trae los PRs de hace un año.

        try {
            log.info("Iniciando sincronización de Pull Requests para {}/{}", owner, repo);
            List<GithubPullRequestDto> pullRequestDtos = githubPullRequestCollector.getPullRequests(owner, repo, lastSync);

            if (pullRequestDtos.isEmpty()) {
                log.info("No se encontraron nuevos Pull Requests.");
                updateSyncStatus(jobName);
                log.info("Sincronización de Pull Requests para {}/{} completada exitosamente.", owner, repo);
                return;
            }

            // Estrategia de consulta masiva para evitar N+1
            Set<Long> incomingIds = pullRequestDtos.stream()
                    .map(GithubPullRequestDto::getId)
                    .collect(Collectors.toSet());

            Set<Long> existingIds = pullRequestRepository.findAllById(incomingIds).stream()
                    .map(PullRequest::getId)
                    .collect(Collectors.toSet());

            List<PullRequest> newPullRequestsToSave = pullRequestDtos.stream()
                    .filter(dto -> !existingIds.contains(dto.getId()))
                    .map(dto -> {
                        PullRequest pr = new PullRequest(dto, config);
                        pr.setFirstCommitSha(dto.getFirstCommitSha());
                        return pr;
                    })
                    .collect(Collectors.toList());

            if (!newPullRequestsToSave.isEmpty()) {
                log.info("Se encontraron {} nuevos Pull Requests para guardar.", newPullRequestsToSave.size());
                pullRequestRepository.saveAll(newPullRequestsToSave);
            } else {
                log.info("Todos los Pull Requests recibidos ya existían en la base de datos.");
            }

            updateSyncStatus(jobName);
            log.info("Sincronización de Pull Requests para {}/{} completada exitosamente.", owner, repo);

        } catch (Exception e) {
            log.error("Error durante la sincronización de Pull Requests para {}/{}: {}", owner, repo, e.getMessage(), e);
        }
    }

    private void updateSyncStatus(String jobName) {
        SyncStatus newSyncStatus = new SyncStatus(jobName, LocalDateTime.now());
        syncStatusRepository.save(newSyncStatus);
    }
}
