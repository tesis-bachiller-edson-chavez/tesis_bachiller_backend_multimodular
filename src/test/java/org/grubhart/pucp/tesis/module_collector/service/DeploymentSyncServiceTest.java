package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_collector.github.GithubClientImpl;
import org.grubhart.pucp.tesis.module_domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentSyncServiceTest {

    @Mock
    private GithubClientImpl githubClient;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private SyncStatusRepository syncStatusRepository;

    @Mock
    private RepositoryConfigRepository repositoryConfigRepository;

    @InjectMocks
    private DeploymentSyncService deploymentSyncService;

    @Captor
    private ArgumentCaptor<List<Deployment>> deploymentsCaptor;

    private static final String VALID_REPO_URL = "https://github.com/owner/repo";
    private static final String WORKFLOW_FILE_NAME = "deploy.yml";

    @BeforeEach
    void setUp() {
        deploymentSyncService = new DeploymentSyncService(
                githubClient,
                deploymentRepository,
                syncStatusRepository,
                repositoryConfigRepository,
                WORKFLOW_FILE_NAME
        );
    }

    private GitHubWorkflowRunDto createWorkflowRunDto(Long id, String name, String conclusion) {
        GitHubWorkflowRunDto dto = new GitHubWorkflowRunDto();
        dto.setId(id);
        dto.setName(name);
        dto.setHeadBranch("main");
        dto.setStatus("completed");
        dto.setConclusion(conclusion);
        dto.setCreatedAt(LocalDateTime.now());
        dto.setUpdatedAt(LocalDateTime.now());
        return dto;
    }

    @Test
    void syncDeployments_shouldSkipExecution_whenNoRepositoriesAreConfigured() {
        when(repositoryConfigRepository.findAll()).thenReturn(Collections.emptyList());

        deploymentSyncService.syncDeployments();

        verify(githubClient, never()).getWorkflowRuns(any(), any(), any(), any());
    }

    @Test
    void syncDeployments_shouldSkipRepository_whenUrlIsInvalid() {
        RepositoryConfig invalidRepoConfig = new RepositoryConfig("invalid-url");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(invalidRepoConfig));

        deploymentSyncService.syncDeployments();

        verify(githubClient, never()).getWorkflowRuns(any(), any(), any(), any());
    }

    @Test
    void syncDeployments_whenAllRunsAreNew_shouldSaveAllOfThem() {
        // Arrange
        RepositoryConfig repoConfig = new RepositoryConfig(VALID_REPO_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(any())).thenReturn(Optional.empty());

        GitHubWorkflowRunDto newRun1 = createWorkflowRunDto(1L, "run1", "success");
        GitHubWorkflowRunDto newRun2 = createWorkflowRunDto(2L, "run2", "success");
        when(githubClient.getWorkflowRuns("owner", "repo", WORKFLOW_FILE_NAME, null)).thenReturn(List.of(newRun1, newRun2));

        when(deploymentRepository.existsById(1L)).thenReturn(false);
        when(deploymentRepository.existsById(2L)).thenReturn(false);

        // Act
        deploymentSyncService.syncDeployments();

        // Assert
        verify(deploymentRepository).saveAll(deploymentsCaptor.capture());
        List<Deployment> savedDeployments = deploymentsCaptor.getValue();
        assertEquals(2, savedDeployments.size());
        assertTrue(savedDeployments.stream().anyMatch(d -> d.getGithubId() == 1L));
        assertTrue(savedDeployments.stream().anyMatch(d -> d.getGithubId() == 2L));
    }

    @Test
    void syncDeployments_whenApiReturnsMixedRuns_shouldOnlySaveNewOnes() {
        // Arrange
        RepositoryConfig repoConfig = new RepositoryConfig(VALID_REPO_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(any())).thenReturn(Optional.empty());

        GitHubWorkflowRunDto existingRun = createWorkflowRunDto(1L, "run1", "success");
        GitHubWorkflowRunDto newRun = createWorkflowRunDto(2L, "run2", "success");
        when(githubClient.getWorkflowRuns("owner", "repo", WORKFLOW_FILE_NAME, null)).thenReturn(List.of(existingRun, newRun));

        when(deploymentRepository.existsById(1L)).thenReturn(true); // Este ya existe
        when(deploymentRepository.existsById(2L)).thenReturn(false); // Este es nuevo

        // Act
        deploymentSyncService.syncDeployments();

        // Assert
        verify(deploymentRepository).saveAll(deploymentsCaptor.capture());
        List<Deployment> savedDeployments = deploymentsCaptor.getValue();
        assertEquals(1, savedDeployments.size());
        assertEquals(2L, savedDeployments.get(0).getGithubId());
    }

    @Test
    void syncDeployments_whenApiReturnsNoNewRuns_shouldNotSaveAnything() {
        // Arrange
        RepositoryConfig repoConfig = new RepositoryConfig(VALID_REPO_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(any())).thenReturn(Optional.empty());

        GitHubWorkflowRunDto existingRun = createWorkflowRunDto(1L, "run1", "success");
        when(githubClient.getWorkflowRuns("owner", "repo", WORKFLOW_FILE_NAME, null)).thenReturn(List.of(existingRun));

        when(deploymentRepository.existsById(1L)).thenReturn(true);

        // Act
        deploymentSyncService.syncDeployments();

        // Assert
        verify(deploymentRepository, never()).saveAll(any());
    }
}
