package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.Commit;
import org.grubhart.pucp.tesis.module_domain.CommitRepository;
import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfigRepository;
import org.grubhart.pucp.tesis.module_domain.SyncStatus;
import org.grubhart.pucp.tesis.module_domain.SyncStatusRepository;
import org.grubhart.pucp.tesis.module_domain.GithubCommitDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CommitSyncService {

    private static final Logger log = LoggerFactory.getLogger(CommitSyncService.class);
    public static final String SYNC_ID_PREFIX = "COMMIT_SYNC_";

    private final CommitRepository commitRepository;
    private final SyncStatusRepository syncStatusRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;
    private final GithubCommitCollector githubCommitCollector;

    public CommitSyncService(CommitRepository commitRepository,
                             SyncStatusRepository syncStatusRepository,
                             RepositoryConfigRepository repositoryConfigRepository,
                             GithubCommitCollector githubCommitCollector) {
        this.commitRepository = commitRepository;
        this.syncStatusRepository = syncStatusRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
        this.githubCommitCollector = githubCommitCollector;
    }

    /**
     * Tarea programada para sincronizar commits desde los repositorios configurados.
     * Se ejecuta 10 segundos después de que la aplicación arranca y luego cada hora.
     */
    @Scheduled(initialDelay = 10000, fixedRate = 3600000)
    public void syncCommits() {
        List<RepositoryConfig> configs = repositoryConfigRepository.findAll();
        if (configs.isEmpty()) {
            log.warn("No hay repositorios configurados para la sincronización de commits.");
            return;
        }

        log.info("Iniciando ciclo de sincronización para {} repositorios configurados.", configs.size());
        for (RepositoryConfig config : configs) {
            syncRepository(config);
        }
        log.info("Ciclo de sincronización de commits finalizado.");
    }

    private void syncRepository(RepositoryConfig config) {
        String owner = config.getOwner();
        String repo = config.getRepoName();

        if (owner == null || repo == null) {
            log.error("La URL del repositorio '{}' no tiene el formato esperado. Saltando sincronización para este repositorio.", config.getRepositoryUrl());
            return;
        }

        String syncId = SYNC_ID_PREFIX + owner + "/" + repo;
        Optional<SyncStatus> syncStatus = syncStatusRepository.findById(syncId);
        LocalDateTime lastSync = syncStatus.map(SyncStatus::getLastSuccessfulRun)
                // Si nunca se ha sincronizado, trae los commits de hace un año.
                .orElse(LocalDateTime.now().minusYears(1));

        try {
            log.info("Iniciando sincronización de commits para {}/{}", owner, repo);
            List<GithubCommitDto> commitDtos = githubCommitCollector.getCommits(owner, repo, lastSync);

            List<Commit> newCommitsToSave = commitDtos.stream()
                    .filter(dto -> !commitRepository.existsById(dto.getSha()))
                    .map(Commit::new) // Usamos el nuevo constructor de Commit
                    .collect(Collectors.toList());

            if (!newCommitsToSave.isEmpty()) {
                log.info("Se encontraron {} nuevos commits para guardar.", newCommitsToSave.size());
                commitRepository.saveAll(newCommitsToSave);
            } else {
                log.info("No se encontraron nuevos commits.");
            }

            SyncStatus newSyncStatus = new SyncStatus(syncId, LocalDateTime.now());
            syncStatusRepository.save(newSyncStatus);
            log.info("Sincronización de commits para {}/{} completada exitosamente.", owner, repo);

        } catch (Exception e) {
            log.error("Error durante la sincronización de commits para {}/{}: {}", owner, repo, e.getMessage(), e);
        }
    }
}
