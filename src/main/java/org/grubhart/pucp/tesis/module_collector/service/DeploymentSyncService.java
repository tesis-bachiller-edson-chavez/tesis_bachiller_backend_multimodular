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
    @Scheduled(fixedRate = 300000)
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
        Optional<SyncStatus> syncStatus = syncStatusRepository.findById(JOB_NAME + "_" + repoName);
        LocalDateTime lastRun = syncStatus.map(SyncStatus::getLastSuccessfulRun).orElse(null);

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
            updateSyncStatus(repoName);
        } else {
            log.info("No se encontraron nuevos deployments para {}/{}.", owner, repoName);
            log.debug("SyncStatus not updated - no new deployments found");
        }

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

    private void updateSyncStatus(String repoName) {
        SyncStatus status = new SyncStatus(JOB_NAME + "_" + repoName, LocalDateTime.now());
        syncStatusRepository.save(status);
    }
}
