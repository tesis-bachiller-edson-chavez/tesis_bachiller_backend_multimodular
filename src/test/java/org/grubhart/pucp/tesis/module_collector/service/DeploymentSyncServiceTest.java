package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.Deployment;
import org.grubhart.pucp.tesis.module_domain.DeploymentRepository;
import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunDto;
import org.grubhart.pucp.tesis.module_domain.GithubDeploymentCollector;
import org.grubhart.pucp.tesis.module_domain.SyncStatus;
import org.grubhart.pucp.tesis.module_domain.SyncStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentSyncServiceTest {

    @Mock
    private GithubDeploymentCollector githubDeploymentCollector;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private SyncStatusRepository syncStatusRepository;

    @InjectMocks
    private DeploymentSyncService deploymentSyncService;

    private final String REPO_OWNER = "test-owner";
    private final String REPO_NAME = "test-repo";
    private final String WORKFLOW_FILENAME = "test-deploy.yml";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deploymentSyncService, "owner", REPO_OWNER);
        ReflectionTestUtils.setField(deploymentSyncService, "repoName", REPO_NAME);
        ReflectionTestUtils.setField(deploymentSyncService, "workflowFileName", WORKFLOW_FILENAME);
    }

    @Test
    void shouldSyncNewDeploymentsAndSkipExistingOnes() {
        // 1. Arrange
        LocalDateTime lastSyncTime = LocalDateTime.now().minusDays(1);
        // Usamos la constante y el constructor correctos
        SyncStatus syncStatus = new SyncStatus(DeploymentSyncService.PROCESS_NAME_DEPLOYMENT, lastSyncTime);

        GitHubWorkflowRunDto existingRunDto = new GitHubWorkflowRunDto();
        existingRunDto.setId(101L);
        existingRunDto.setConclusion("success");
        existingRunDto.setCreatedAt(lastSyncTime.plusHours(1));

        GitHubWorkflowRunDto newRunDto = new GitHubWorkflowRunDto();
        newRunDto.setId(102L);
        newRunDto.setConclusion("failure");
        newRunDto.setCreatedAt(lastSyncTime.plusHours(2));

        List<GitHubWorkflowRunDto> runsFromApi = List.of(existingRunDto, newRunDto);

        // Mockeamos usando la constante correcta del servicio
        when(syncStatusRepository.findById(DeploymentSyncService.PROCESS_NAME_DEPLOYMENT))
                .thenReturn(Optional.of(syncStatus));

        // El servicio llamará a getWorkflowRuns con la fecha obtenida de getLastSuccessfulRun()
        when(githubDeploymentCollector.getWorkflowRuns(REPO_OWNER, REPO_NAME, WORKFLOW_FILENAME, lastSyncTime))
                .thenReturn(runsFromApi);

        when(deploymentRepository.findByGithubId(101L)).thenReturn(Optional.of(new Deployment()));
        when(deploymentRepository.findByGithubId(102L)).thenReturn(Optional.empty());

        // 2. Act
        deploymentSyncService.sync();

        // 3. Assert
        verify(deploymentRepository, times(1)).findByGithubId(101L);
        verify(deploymentRepository, times(1)).findByGithubId(102L);

        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository, times(1)).save(deploymentCaptor.capture());
        assertEquals(102L, deploymentCaptor.getValue().getGithubId());
        assertEquals("failure", deploymentCaptor.getValue().getConclusion());

        verify(deploymentRepository, never()).save(argThat(d -> d.getGithubId() != null && d.getGithubId().equals(101L)));

        // Verificamos que se guarda el nuevo estado de la sincronización
        ArgumentCaptor<SyncStatus> syncStatusCaptor = ArgumentCaptor.forClass(SyncStatus.class);
        verify(syncStatusRepository, times(1)).save(syncStatusCaptor.capture());
        // *** LA CORRECCIÓN ESTÁ AQUÍ ***
        assertEquals(DeploymentSyncService.PROCESS_NAME_DEPLOYMENT, syncStatusCaptor.getValue().getJobName());
    }
}
