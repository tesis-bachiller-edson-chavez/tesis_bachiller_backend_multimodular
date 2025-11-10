package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_collector.DeploymentSyncTrigger;
import org.grubhart.pucp.tesis.module_collector.github.GithubClientImpl;
import org.grubhart.pucp.tesis.module_domain.*;
import org.grubhart.pucp.tesis.module_processor.LeadTimeCalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DeploymentSyncService implements DeploymentSyncTrigger {

    private static final Logger log = LoggerFactory.getLogger(DeploymentSyncService.class);
    private static final String JOB_NAME = "DEPLOYMENT_SYNC";

    private final GithubClientImpl gitHubClient;
    private final DeploymentRepository deploymentRepository;
    private final SyncStatusRepository syncStatusRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;
    private final LeadTimeCalculationService leadTimeCalculationService;

    public DeploymentSyncService(GithubClientImpl gitHubClient,
                                 DeploymentRepository deploymentRepository,
                                 SyncStatusRepository syncStatusRepository,
                                 RepositoryConfigRepository repositoryConfigRepository,
                                 LeadTimeCalculationService leadTimeCalculationService) {
        this.gitHubClient = gitHubClient;
        this.deploymentRepository = deploymentRepository;
        this.syncStatusRepository = syncStatusRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
        this.leadTimeCalculationService = leadTimeCalculationService;
    }

    @Override
    @Scheduled(fixedRate = 3600000)
    public void syncDeployments() {
        log.info("Iniciando la sincronización de deployments para todos los repositorios configurados.");

        List<RepositoryConfig> repositories = repositoryConfigRepository.findAll();
        if (repositories.isEmpty()) {
            log.warn("No hay repositorios configurados para sincronizar. Finalizando el job.");
            return;
        }

        for (RepositoryConfig repoConfig : repositories) {
            try {
                String owner = repoConfig.getOwner();
                String repoName = repoConfig.getRepoName();
                String workflowFileName = repoConfig.getDeploymentWorkflowFileName();

                if (owner == null || repoName == null || workflowFileName == null || workflowFileName.isBlank()) {
                    log.warn("Omitiendo repositorio {} - configuración inválida (owner, repo o nombre de archivo de workflow faltante)", repoConfig.getRepositoryUrl());
                    continue;
                }

                log.info("Sincronizando deployments para el repositorio: {}/{} usando el workflow '{}'", owner, repoName, workflowFileName);
                syncDeploymentsForRepository(owner, repoName, workflowFileName, repoConfig);

            } catch (IllegalArgumentException e) {
                log.error("URL de repositorio no válida en la configuración: '{}'. Saltando este repositorio.", repoConfig.getRepositoryUrl(), e);
            } catch (Exception e) {
                log.error("Error inesperado durante la sincronización del repositorio {}: {}", repoConfig.getRepositoryUrl(), e.getMessage(), e);
            }
        }
        log.info("Sincronización de deployments completada para todos los repositorios.");
    }

    private void syncDeploymentsForRepository(String owner, String repoName, String workflowFileName, RepositoryConfig repositoryConfig) {
        // Detect workflow change BEFORE getting sync status
        boolean workflowChanged = detectAndHandleWorkflowChange(repositoryConfig, workflowFileName);

        // Build sync key with workflow filename (Option 1: include workflow in key)
        String syncKey = JOB_NAME + "_" + repoName + "_" + workflowFileName;
        Optional<SyncStatus> syncStatus = syncStatusRepository.findById(syncKey);

        // If workflow changed, ignore existing sync status to force full sync
        LocalDateTime lastRun = (!workflowChanged && syncStatus.isPresent())
                ? syncStatus.get().getLastSuccessfulRun()
                : null;

        if (lastRun == null) {
            log.info("Starting full sync for {}/{} with workflow '{}' (capturing all historical deployments)",
                    owner, repoName, workflowFileName);
        }

        List<GitHubWorkflowRunDto> workflowRuns = gitHubClient.getWorkflowRuns(owner, repoName, workflowFileName, lastRun);

        List<Deployment> newDeployments = new ArrayList<>();
        for (GitHubWorkflowRunDto run : workflowRuns) {
            if (!"success".equals(run.getConclusion())) {
                continue;
            }
            try {
                Deployment deployment = convertToDeployment(run, repositoryConfig);
                if (!deploymentRepository.existsById(deployment.getGithubId())) {
                    newDeployments.add(deployment);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Omitiendo despliegue con ID de workflow {} por no tener un SHA de commit válido.", run.getId());
            }
        }

        if (!newDeployments.isEmpty()) {
            deploymentRepository.saveAll(newDeployments);
            log.info("Se guardaron {} nuevos deployments para {}/{}.", newDeployments.size(), owner, repoName);
            leadTimeCalculationService.calculate();
        } else {
            log.info("No se encontraron nuevos deployments para {}/{}.", owner, repoName);
        }

        updateSyncStatus(repoName, workflowFileName);
        log.info("Sincronización de deployments para {}/{} completada exitosamente.", owner, repoName);
    }

    private Deployment convertToDeployment(GitHubWorkflowRunDto dto, RepositoryConfig repositoryConfig) {
        if (dto.getHeadSha() == null || dto.getHeadSha().isBlank()) {
            throw new IllegalArgumentException("El SHA del commit es nulo o está vacío.");
        }
        Deployment deployment = new Deployment();
        deployment.setGithubId(dto.getId());
        deployment.setRepository(repositoryConfig);
        deployment.setName(dto.getName());
        deployment.setHeadBranch(dto.getHeadBranch());
        deployment.setSha(dto.getHeadSha());
        deployment.setServiceName(repositoryConfig.getDatadogServiceName());
        deployment.setStatus(dto.getStatus());
        deployment.setConclusion(dto.getConclusion());
        deployment.setCreatedAt(dto.getCreatedAt());
        deployment.setUpdatedAt(dto.getUpdatedAt());
        if ("main".equals(dto.getHeadBranch())) {
            deployment.setEnvironment("production");
        }
        return deployment;
    }

    private void updateSyncStatus(String repoName, String workflowFileName) {
        String syncKey = JOB_NAME + "_" + repoName + "_" + workflowFileName;
        SyncStatus status = new SyncStatus(syncKey, LocalDateTime.now());
        syncStatusRepository.save(status);
        log.debug("Updated sync status: {}", syncKey);
    }

    /**
     * Detects if the workflow filename has changed and resets sync status if needed.
     * This method implements Option 3: tracking the last synced workflow file.
     *
     * @param repoConfig The repository configuration
     * @param currentWorkflow The current workflow filename from configuration
     * @return true if workflow changed (sync should start from beginning), false otherwise
     */
    private boolean detectAndHandleWorkflowChange(RepositoryConfig repoConfig, String currentWorkflow) {
        String lastWorkflow = repoConfig.getLastSyncedWorkflowFile();

        // First time syncing this repo
        if (lastWorkflow == null) {
            log.info("First sync for repository {}. Setting workflow: {}",
                    repoConfig.getRepositoryUrl(), currentWorkflow);
            repoConfig.setLastSyncedWorkflowFile(currentWorkflow);
            repositoryConfigRepository.save(repoConfig);
            return true; // Treat as change to force full sync
        }

        // Workflow changed
        if (!currentWorkflow.equals(lastWorkflow)) {
            log.warn("Workflow filename changed for repository {} from '{}' to '{}'. " +
                            "Sync will restart from beginning to capture historical deployments.",
                    repoConfig.getRepositoryUrl(), lastWorkflow, currentWorkflow);

            repoConfig.setLastSyncedWorkflowFile(currentWorkflow);
            repositoryConfigRepository.save(repoConfig);
            return true; // Force full sync
        }

        // No change
        return false;
    }
}
