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
    @Scheduled(initialDelay = 2880000, fixedRate = 3600000) // Every hour, starts at 48 min
    public void syncDeployments() {
        log.info("Iniciando la sincronización de deployments para todos los repositorios configurados.");

        List<RepositoryConfig> repositories = repositoryConfigRepository.findAll();
        if (repositories.isEmpty()) {
            log.warn("No hay repositorios configurados para sincronizar. Finalizando el job.");
            return;
        }

        for (int i = 0; i < repositories.size(); i++) {
            RepositoryConfig repoConfig = repositories.get(i);
            try {
                String owner = repoConfig.getOwner();
                String repoName = repoConfig.getRepoName();
                String workflowFileName = repoConfig.getDeploymentWorkflowFileName();

                String productionEnvName = repoConfig.getProductionEnvironmentName();

                if (owner == null || repoName == null || workflowFileName == null || workflowFileName.isBlank()) {
                    log.warn("Omitiendo repositorio {} - configuración inválida (owner, repo o nombre de archivo de workflow faltante)", repoConfig.getRepositoryUrl());
                    continue;
                }

                if (productionEnvName == null || productionEnvName.isBlank()) {
                    log.debug("Omitiendo repositorio {} - productionEnvironmentName no configurado", repoConfig.getRepositoryUrl());
                    continue;
                }

                log.info("Sincronizando deployments para el repositorio: {}/{} usando el workflow '{}'", owner, repoName, workflowFileName);
                syncDeploymentsForRepository(owner, repoName, workflowFileName, repoConfig);

            } catch (IllegalArgumentException e) {
                log.error("URL de repositorio no válida en la configuración: '{}'. Saltando este repositorio.", repoConfig.getRepositoryUrl(), e);
            } catch (Exception e) {
                log.error("Error inesperado durante la sincronización del repositorio {}: {}", repoConfig.getRepositoryUrl(), e.getMessage(), e);
            }

            // Add delay between repositories to avoid hitting GitHub rate limit
            if (i < repositories.size() - 1) {
                try {
                    Thread.sleep(2000); // 2 seconds delay between repos
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Sincronización de deployments interrumpida");
                    return;
                }
            }
        }
        log.info("Sincronización de deployments completada para todos los repositorios.");
    }

    private void syncDeploymentsForRepository(String owner, String repoName, String workflowFileName, RepositoryConfig repositoryConfig) {
        Optional<SyncStatus> syncStatus = syncStatusRepository.findById(JOB_NAME + "_" + repoName);
        LocalDateTime lastRun = syncStatus.map(SyncStatus::getLastSuccessfulRun).orElse(null);

        List<GitHubWorkflowRunDto> workflowRuns = gitHubClient.getWorkflowRuns(owner, repoName, workflowFileName, lastRun);

        List<Deployment> newDeployments = new ArrayList<>();
        int skippedByConclusion = 0;
        int skippedByEnvironment = 0;
        int skippedByExisting = 0;

        for (GitHubWorkflowRunDto run : workflowRuns) {
            if (!"success".equals(run.getConclusion())) {
                skippedByConclusion++;
                continue;
            }

            // Filtrar solo deployments a producción
            // El display_title tiene formato: "Deploy to {env} by @username"
            if (!isProductionDeployment(run, repositoryConfig)) {
                skippedByEnvironment++;
                log.debug("Omitiendo deployment {} - no es un deployment a producción (displayTitle: {}, envName: {})",
                        run.getId(), run.getDisplayTitle(), repositoryConfig.getProductionEnvironmentName());
                continue;
            }

            try {
                Deployment deployment = convertToDeployment(run, repositoryConfig);
                if (!deploymentRepository.existsById(deployment.getGithubId())) {
                    newDeployments.add(deployment);
                } else {
                    skippedByExisting++;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Omitiendo despliegue con ID de workflow {} por no tener un SHA de commit válido.", run.getId());
            }
        }

        // Log resumen de filtrado
        if (workflowRuns.size() > 0) {
            log.info("Resumen {}/{}: total={}, filtrados=[conclusion={}, ambiente={}, existentes={}], nuevos={}",
                    owner, repoName, workflowRuns.size(), skippedByConclusion, skippedByEnvironment, skippedByExisting, newDeployments.size());
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

    /**
     * Determina si un workflow run es un deployment a producción.
     * El display_title del workflow tiene formato: "Deploy to {environment} by @{actor}"
     * Solo se consideran deployments a producción cuando environment coincide con el valor configurado.
     *
     * @param run El workflow run a evaluar
     * @param repositoryConfig La configuración del repositorio con el nombre del ambiente de producción
     * @return true si es un deployment a producción, false en caso contrario
     */
    private boolean isProductionDeployment(GitHubWorkflowRunDto run, RepositoryConfig repositoryConfig) {
        // Obtener el nombre del ambiente de producción configurado
        String envName = repositoryConfig.getProductionEnvironmentName();
        if (envName == null || envName.isBlank()) {
            // Si no hay configuración de ambiente, rechazar para evitar contar deployments no productivos
            log.debug("Workflow run {} - no hay productionEnvironmentName configurado, rechazando deployment", run.getId());
            return false;
        }

        String displayTitle = run.getDisplayTitle();
        if (displayTitle == null || displayTitle.isBlank()) {
            // Si no hay display_title pero sí hay envName configurado, rechazar
            log.debug("Workflow run {} no tiene display_title pero hay envName configurado ({}), rechazando",
                    run.getId(), envName);
            return false;
        }

        // Buscar patrones que indiquen deployment al ambiente configurado
        // Formatos soportados:
        // - "Deploy to prod by @username" (workflow con run-name dinámico)
        // - "Deploy Prod" (workflow con nombre fijo)
        // - "Deploy - prod" (workflow con run-name simple)
        String lowerTitle = displayTitle.toLowerCase();
        String lowerEnvName = envName.toLowerCase();
        return lowerTitle.contains(lowerEnvName);
    }
}
