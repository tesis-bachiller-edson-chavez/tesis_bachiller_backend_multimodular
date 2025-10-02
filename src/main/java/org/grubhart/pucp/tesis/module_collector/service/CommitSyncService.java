package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.Commit;
import org.grubhart.pucp.tesis.module_domain.CommitParent;
import org.grubhart.pucp.tesis.module_domain.CommitParentRepository;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CommitSyncService {

    private static final Logger log = LoggerFactory.getLogger(CommitSyncService.class);
    public static final String SYNC_ID_PREFIX = "COMMIT_SYNC_";

    private final CommitRepository commitRepository;
    private final CommitParentRepository commitParentRepository;
    private final SyncStatusRepository syncStatusRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;
    private final GithubCommitCollector githubCommitCollector;

    public CommitSyncService(CommitRepository commitRepository,
                             CommitParentRepository commitParentRepository,
                             SyncStatusRepository syncStatusRepository,
                             RepositoryConfigRepository repositoryConfigRepository,
                             GithubCommitCollector githubCommitCollector) {
        this.commitRepository = commitRepository;
        this.commitParentRepository = commitParentRepository;
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
        String repoName = config.getRepoName();

        if (!StringUtils.hasText(owner) || !StringUtils.hasText(repoName)) {
            log.warn("Configuración de repositorio inválida. Se omite la sincronización. Owner: '{}', Repo: '{}'", owner, repoName);
            return;
        }

        String syncId = SYNC_ID_PREFIX + owner + "/" + repoName;
        Optional<SyncStatus> syncStatus = syncStatusRepository.findById(syncId);
        LocalDateTime lastSync = syncStatus.map(SyncStatus::getLastSuccessfulRun)
                // Si nunca se ha sincronizado, trae los commits de hace un año.
                .orElse(LocalDateTime.now().minusYears(1));

        try {
            log.info("Iniciando sincronización de commits para {}/{}", owner, repoName);
            List<GithubCommitDto> commitDtos = githubCommitCollector.getCommits(owner, repoName, lastSync);

            // First pass: Save all commits that don't exist yet
            List<Commit> newCommitsToSave = commitDtos.stream()
                    .filter(dto -> !commitRepository.existsById(dto.getSha()))
                    .map(Commit::new)
                    .collect(Collectors.toList());

            if (!newCommitsToSave.isEmpty()) {
                log.info("Se encontraron {} nuevos commits para guardar.", newCommitsToSave.size());
                commitRepository.saveAll(newCommitsToSave);
            }

            // Second pass: Create and save parent relationships by fetching the entities
            List<CommitParent> newCommitParents = commitDtos.stream()
                    .flatMap(dto -> {
                        // Find the child commit entity we just saved
                        Optional<Commit> childCommitOpt = commitRepository.findById(dto.getSha());
                        if (childCommitOpt.isEmpty()) {
                            log.warn("Commit hijo con SHA {} no encontrado en la BD, no se pueden crear sus relaciones de parentesco.", dto.getSha());
                            return Stream.empty();
                        }
                        Commit childCommit = childCommitOpt.get();

                        // For each parent in the DTO, find the parent entity and create the relationship
                        return dto.getParents().stream()
                                .map(parentDto -> {
                                    Optional<Commit> parentCommitOpt = commitRepository.findById(parentDto.getSha());
                                    if (parentCommitOpt.isEmpty()) {
                                        // This can happen if the parent is older than our sync window (e.g., >1 year)
                                        log.debug("Parent commit with SHA {} not found for child {}, skipping relationship.", parentDto.getSha(), childCommit.getSha());
                                        return null;
                                    }
                                    Commit parentCommit = parentCommitOpt.get();
                                    return new CommitParent(childCommit, parentCommit);
                                })
                                .filter(Objects::nonNull); // Filter out the nulls from parents not found
                    })
                    .collect(Collectors.toList());


            if (!newCommitParents.isEmpty()) {
                log.info("Se encontraron {} nuevas relaciones de parentesco para guardar.", newCommitParents.size());
                commitParentRepository.saveAll(newCommitParents);
            }

            if (newCommitsToSave.isEmpty() && newCommitParents.isEmpty()) {
                log.info("No se encontraron nuevos commits ni relaciones de parentesco para {}/{}.", owner, repoName);
            }

            SyncStatus newSyncStatus = new SyncStatus(syncId, LocalDateTime.now());
            syncStatusRepository.save(newSyncStatus);
            log.info("Sincronización de commits para {}/{} completada exitosamente.", owner, repoName);

        } catch (Exception e) {
            log.error("Error durante la sincronización de commits para {}/{}: {}", owner, repoName, e.getMessage(), e);
        }
    }
}
