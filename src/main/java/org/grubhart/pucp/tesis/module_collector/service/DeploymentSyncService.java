package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeploymentSyncService {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentSyncService.class);
    public static final String PROCESS_NAME_DEPLOYMENT = "deployment-sync";

    private final GithubDeploymentCollector githubDeploymentCollector;
    private final DeploymentRepository deploymentRepository;
    private final SyncStatusRepository syncStatusRepository;

    private final String owner;
    private final String repoName;
    private final String workflowFileName;

    public DeploymentSyncService(GithubDeploymentCollector githubDeploymentCollector,
                                 DeploymentRepository deploymentRepository,
                                 SyncStatusRepository syncStatusRepository,
                                 @Value("${dora.github.owner}") String owner,
                                 @Value("${dora.github.repo}") String repoName,
                                 @Value("${dora.github.workflow-file-name}") String workflowFileName) {
        this.githubDeploymentCollector = githubDeploymentCollector;
        this.deploymentRepository = deploymentRepository;
        this.syncStatusRepository = syncStatusRepository;
        this.owner = owner;
        this.repoName = repoName;
        this.workflowFileName = workflowFileName;
    }


    public void sync() {
        logger.info("Iniciando la sincronización de Deployments para el repositorio {}/{}", owner, repoName);

        LocalDateTime lastSyncTime = syncStatusRepository.findById(PROCESS_NAME_DEPLOYMENT)
                .map(SyncStatus::getLastSuccessfulRun)
                .orElse(LocalDateTime.now().minusYears(1)); // Valor por defecto para la primera ejecución

        logger.info("Obteniendo ejecuciones de workflow desde: {}", lastSyncTime);

        List<GitHubWorkflowRunDto> workflowRuns = githubDeploymentCollector.getWorkflowRuns(owner, repoName, workflowFileName, lastSyncTime);

        logger.info("Se encontraron {} ejecuciones de workflow para procesar.", workflowRuns.size());

        for (GitHubWorkflowRunDto runDto : workflowRuns) {
            deploymentRepository.findByGithubId(runDto.getId()).ifPresentOrElse(
                    existingDeployment -> {
                        logger.debug("El deployment con GitHub ID {} ya existe. Omitiendo.", runDto.getId());
                    },
                    () -> {
                        logger.info("Creando nuevo deployment para GitHub ID {}.", runDto.getId());
                        Deployment newDeployment = mapDtoToEntity(runDto);
                        deploymentRepository.save(newDeployment);
                    }
            );
        }

        SyncStatus newSyncStatus = new SyncStatus(PROCESS_NAME_DEPLOYMENT, LocalDateTime.now());
        syncStatusRepository.save(newSyncStatus);

        logger.info("Sincronización de Deployments finalizada.");
    }

    private Deployment mapDtoToEntity(GitHubWorkflowRunDto dto) {
        return new Deployment(
                dto.getId(),
                dto.getName(),
                dto.getHeadBranch(),
                dto.getStatus(),
                dto.getConclusion(),
                dto.getCreatedAt(),
                dto.getUpdatedAt()
        );
    }
}
