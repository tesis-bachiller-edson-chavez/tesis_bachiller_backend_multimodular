package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_collector.github.GithubClientImpl;
import org.grubhart.pucp.tesis.module_domain.Deployment;
import org.grubhart.pucp.tesis.module_domain.DeploymentRepository;
import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunDto;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfigRepository;
import org.grubhart.pucp.tesis.module_domain.SyncStatus;
import org.grubhart.pucp.tesis.module_domain.SyncStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DeploymentSyncService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentSyncService.class);
    private static final String JOB_NAME = "DEPLOYMENT_SYNC";

    private final GithubClientImpl gitHubClient;
    private final DeploymentRepository deploymentRepository;
    private final SyncStatusRepository syncStatusRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;

    private final String workflowFileName;

    public DeploymentSyncService(GithubClientImpl gitHubClient,
                                 DeploymentRepository deploymentRepository,
                                 SyncStatusRepository syncStatusRepository,
                                 RepositoryConfigRepository repositoryConfigRepository,
                                 @Value("${dora.github.workflow-file-name}") String workflowFileName) {
        this.gitHubClient = gitHubClient;
        this.deploymentRepository = deploymentRepository;
        this.syncStatusRepository = syncStatusRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
        this.workflowFileName = workflowFileName;
    }

    @Scheduled(initialDelay = 30000, fixedRate = 3600000)
    public void syncDeployments() {
        log.info("Iniciando la sincronización de deployments para todos los repositorios configurados.");

        List<RepositoryConfig> repositories = repositoryConfigRepository.findAll();
        if (repositories.isEmpty()) {
            log.warn("No hay repositorios configurados para sincronizar. Finalizando el job.");
            return;
        }

        for (RepositoryConfig repoConfig : repositories) {
            try {
                String[] urlParts = parseRepoUrl(repoConfig.getRepositoryUrl());
                String owner = urlParts[0];
                String repoName = urlParts[1];
                log.info("Sincronizando deployments para el repositorio: {}/{}", owner, repoName);
                syncDeploymentsForRepository(owner, repoName);
            } catch (IllegalArgumentException e) {
                log.error("URL de repositorio no válida en la configuración: '{}'. Saltando este repositorio.", repoConfig.getRepositoryUrl(), e);
            } catch (Exception e) {
                log.error("Error inesperado durante la sincronización del repositorio {}: {}", repoConfig.getRepositoryUrl(), e.getMessage(), e);
            }
        }
        log.info("Sincronización de deployments completada para todos los repositorios.");
    }

    private void syncDeploymentsForRepository(String owner, String repoName) throws Exception {
        Optional<SyncStatus> syncStatus = syncStatusRepository.findById(JOB_NAME + "_" + repoName);
        LocalDateTime lastRun = syncStatus.map(SyncStatus::getLastSuccessfulRun).orElse(null);


            List<GitHubWorkflowRunDto> workflowRuns = gitHubClient.getWorkflowRuns(owner, repoName, workflowFileName, lastRun);

            List<Deployment> newDeployments = workflowRuns.stream()
                    .filter(run -> "success".equals(run.getConclusion()))
                    .map(this::convertToDeployment)
                    .filter(deployment -> !deploymentRepository.existsById(deployment.getGithubId()))
                    .collect(Collectors.toList());

            if (!newDeployments.isEmpty()) {
                deploymentRepository.saveAll(newDeployments);
                log.info("Se guardaron {} nuevos deployments para {}/{}.", newDeployments.size(), owner, repoName);
            } else {
                log.info("No se encontraron nuevos deployments para {}/{}.", owner, repoName);
            }

            updateSyncStatus(repoName);
            log.info("Sincronización de deployments para {}/{} completada exitosamente.", owner, repoName);

    }

    private Deployment convertToDeployment(GitHubWorkflowRunDto dto) {
        Deployment deployment = new Deployment();
        deployment.setGithubId(dto.getId());
        deployment.setName(dto.getName());
        deployment.setHeadBranch(dto.getHeadBranch());
        deployment.setStatus(dto.getStatus());
        deployment.setConclusion(dto.getConclusion());
        deployment.setCreatedAt(dto.getCreatedAt());
        deployment.setUpdatedAt(dto.getUpdatedAt());
        return deployment;
    }

    private void updateSyncStatus(String repoName) {
        SyncStatus status = new SyncStatus(JOB_NAME + "_" + repoName, LocalDateTime.now());
        syncStatusRepository.save(status);
    }

    private String[] parseRepoUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("La URL del repositorio no puede ser nula o vacía.");
        }
        String[] parts = url.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Formato de URL no válido: " + url);
        }
        String repoName = parts[parts.length - 1];
        String owner = parts[parts.length - 2];
        return new String[]{owner, repoName};
    }
}
