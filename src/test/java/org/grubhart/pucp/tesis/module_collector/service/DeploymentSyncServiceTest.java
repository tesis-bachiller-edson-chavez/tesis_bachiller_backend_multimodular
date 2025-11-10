package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_collector.github.GithubClientImpl;
import org.grubhart.pucp.tesis.module_domain.*;
import org.grubhart.pucp.tesis.module_processor.LeadTimeCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @Mock
    private LeadTimeCalculationService leadTimeCalculationService;

    @Captor
    private ArgumentCaptor<List<Deployment>> deploymentCaptor;

    private DeploymentSyncService deploymentSyncService;

    @BeforeEach
    void setUp() {
        deploymentSyncService = new DeploymentSyncService(
                githubClient,
                deploymentRepository,
                syncStatusRepository,
                repositoryConfigRepository,
                leadTimeCalculationService
        );
    }

    @Test
    @DisplayName("GIVEN no repositories configured WHEN syncing deployments THEN should log warning and exit")
    void shouldLogWarningAndExitWhenNoRepositoriesConfigured() {
        // Given
        when(repositoryConfigRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(githubClient, never()).getWorkflowRuns(anyString(), anyString(), anyString(), any(LocalDateTime.class));
        verify(deploymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("GIVEN repository with null owner WHEN syncing THEN should skip")
    void shouldSkipRepositoryWithNullOwner() {
        // Given
        RepositoryConfig repoConfig = mock(RepositoryConfig.class);
        when(repoConfig.getOwner()).thenReturn(null);
        when(repoConfig.getRepoName()).thenReturn("repo");
        when(repoConfig.getDeploymentWorkflowFileName()).thenReturn("deploy.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(githubClient, never()).getWorkflowRuns(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GIVEN repository with null repoName WHEN syncing THEN should skip")
    void shouldSkipRepositoryWithNullRepoName() {
        // Given
        RepositoryConfig repoConfig = mock(RepositoryConfig.class);
        when(repoConfig.getOwner()).thenReturn("owner");
        when(repoConfig.getRepoName()).thenReturn(null);
        when(repoConfig.getDeploymentWorkflowFileName()).thenReturn("deploy.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(githubClient, never()).getWorkflowRuns(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    @DisplayName("GIVEN repository with blank workflow file name WHEN syncing THEN should skip")
    void shouldSkipRepositoryWithBlankWorkflowFileName(String blankWorkflowFile) {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/owner/repo");
        repoConfig.setDeploymentWorkflowFileName(blankWorkflowFile);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(githubClient, never()).getWorkflowRuns(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GIVEN repository with null workflow file name WHEN syncing THEN should skip")
    void shouldSkipRepositoryWithNullWorkflowFileName() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/owner/repo");
        repoConfig.setDeploymentWorkflowFileName(null);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(githubClient, never()).getWorkflowRuns(any(), any(), any(), any());
    }

    @Test
    @DisplayName("GIVEN an IllegalArgumentException during config parsing WHEN syncing THEN should continue with next repo")
    void shouldContinueOnIllegalArgumentException() {
        // Given
        RepositoryConfig badRepo = mock(RepositoryConfig.class);
        when(badRepo.getRepositoryUrl()).thenReturn("bad-url");
        when(badRepo.getOwner()).thenThrow(new IllegalArgumentException("Test Exception"));

        RepositoryConfig goodRepo = new RepositoryConfig("https://github.com/owner/good-repo");
        goodRepo.setDeploymentWorkflowFileName("deploy.yml");

        when(repositoryConfigRepository.findAll()).thenReturn(List.of(badRepo, goodRepo));

        // When
        deploymentSyncService.syncDeployments();

        // Then
        // Verify it attempted to sync the good repo
        verify(githubClient, times(1)).getWorkflowRuns(eq("owner"), eq("good-repo"), eq("deploy.yml"), any());
    }

    @Test
    @DisplayName("GIVEN an unexpected exception during one repo sync WHEN syncing THEN should continue with next repo")
    void shouldContinueOnUnexpectedException() {
        // Given
        RepositoryConfig failingRepo = new RepositoryConfig("https://github.com/owner/failing-repo");
        failingRepo.setDeploymentWorkflowFileName("deploy.yml");
        RepositoryConfig workingRepo = new RepositoryConfig("https://github.com/owner/working-repo");
        workingRepo.setDeploymentWorkflowFileName("deploy.yml");

        when(repositoryConfigRepository.findAll()).thenReturn(List.of(failingRepo, workingRepo));

        // Make the first repo sync fail with an unexpected exception
        when(githubClient.getWorkflowRuns("owner", "failing-repo", "deploy.yml", null))
                .thenThrow(new RuntimeException("Unexpected API error"));

        // Make the second repo sync succeed
        when(githubClient.getWorkflowRuns("owner", "working-repo", "deploy.yml", null))
                .thenReturn(Collections.emptyList());

        // When
        deploymentSyncService.syncDeployments();

        // Then
        // Verify both were attempted
        verify(githubClient, times(1)).getWorkflowRuns("owner", "failing-repo", "deploy.yml", null);
        verify(githubClient, times(1)).getWorkflowRuns("owner", "working-repo", "deploy.yml", null);
    }

    @Test
    @DisplayName("GIVEN a workflow run with null head SHA WHEN syncing THEN should skip and log warning")
    void shouldSkipRunWhenHeadShaIsNull() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/owner/repo");
        repoConfig.setDeploymentWorkflowFileName("deploy.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        GitHubWorkflowRunDto validRun = createWorkflowRun(1L, "sha1", "success", "main");
        GitHubWorkflowRunDto invalidRun = createWorkflowRun(2L, null, "success", "main"); // Null SHA

        when(githubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(List.of(validRun, invalidRun));

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(deploymentRepository).saveAll(deploymentCaptor.capture());
        List<Deployment> savedDeployments = deploymentCaptor.getValue();
        assertThat(savedDeployments).hasSize(1);
        assertThat(savedDeployments.get(0).getGithubId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GIVEN a workflow run with blank head SHA WHEN syncing THEN should skip and log warning")
    void shouldSkipRunWhenHeadShaIsBlank() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/owner/repo");
        repoConfig.setDeploymentWorkflowFileName("deploy.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        GitHubWorkflowRunDto validRun = createWorkflowRun(1L, "sha1", "success", "main");
        GitHubWorkflowRunDto invalidRun = createWorkflowRun(2L, " ", "success", "main"); // Blank SHA

        when(githubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(List.of(validRun, invalidRun));

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(deploymentRepository).saveAll(deploymentCaptor.capture());
        List<Deployment> savedDeployments = deploymentCaptor.getValue();
        assertThat(savedDeployments).hasSize(1);
        assertThat(savedDeployments.get(0).getGithubId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GIVEN a workflow run on a non-main branch WHEN converting THEN environment should not be production")
    void shouldNotSetProductionEnvironmentForNonMainBranch() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/owner/repo");
        repoConfig.setDeploymentWorkflowFileName("deploy.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        List<GitHubWorkflowRunDto> workflowRuns = List.of(
                createWorkflowRun(1L, "sha1", "success", "develop") // Non-main branch
        );
        when(githubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(workflowRuns);

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(deploymentRepository).saveAll(deploymentCaptor.capture());
        List<Deployment> savedDeployments = deploymentCaptor.getValue();
        assertThat(savedDeployments).hasSize(1);
        assertThat(savedDeployments.get(0).getEnvironment()).isNotEqualTo("production");
    }

    @Test
    @DisplayName("GIVEN successful workflow runs WHEN syncing THEN should save new deployments")
    void shouldSaveNewDeploymentsOnSuccessfulSync() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/owner/repo");
        repoConfig.setDeploymentWorkflowFileName("deploy.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        List<GitHubWorkflowRunDto> workflowRuns = List.of(
                createWorkflowRun(1L, "sha1", "success", "main")
        );
        when(githubClient.getWorkflowRuns(eq("owner"), eq("repo"), eq("deploy.yml"), any())).thenReturn(workflowRuns);

        when(deploymentRepository.existsById(1L)).thenReturn(false);

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(deploymentRepository).saveAll(anyList());
        verify(leadTimeCalculationService).calculate();
        verify(syncStatusRepository).save(any(SyncStatus.class));
    }

    @Test
    @DisplayName("GIVEN workflow run with non-success conclusion WHEN syncing THEN should skip it")
    void shouldSkipNonSuccessfulWorkflowRuns() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/owner/repo");
        repoConfig.setDeploymentWorkflowFileName("deploy.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        List<GitHubWorkflowRunDto> workflowRuns = List.of(
                createWorkflowRun(1L, "sha1", "failure", "main")
        );
        when(githubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(workflowRuns);

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(deploymentRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("GIVEN existing deployment WHEN syncing THEN should not save it again")
    void shouldNotSaveExistingDeployments() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/owner/repo");
        repoConfig.setDeploymentWorkflowFileName("deploy.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        List<GitHubWorkflowRunDto> workflowRuns = List.of(
                createWorkflowRun(1L, "sha1", "success", "main")
        );
        when(githubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(workflowRuns);

        when(deploymentRepository.existsById(1L)).thenReturn(true);

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(deploymentRepository, never()).saveAll(anyList());
        verify(leadTimeCalculationService, never()).calculate(); // Should not trigger if no new deployments
    }

    @Test
    @DisplayName("Dado múltiples repositorios, debe sincronizar deployments para todos")
    void shouldSyncForAllConfiguredRepositories() {
        // GIVEN
        RepositoryConfig repo1 = new RepositoryConfig("https://github.com/owner1/repo1");
        repo1.setDeploymentWorkflowFileName("deploy1.yml");
        RepositoryConfig repo2 = new RepositoryConfig("https://github.com/owner2/repo2");
        repo2.setDeploymentWorkflowFileName("deploy2.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo1, repo2));

        // WHEN
        deploymentSyncService.syncDeployments();

        // THEN
        verify(githubClient, times(1)).getWorkflowRuns(eq("owner1"), eq("repo1"), eq("deploy1.yml"), any());
        verify(githubClient, times(1)).getWorkflowRuns(eq("owner2"), eq("repo2"), eq("deploy2.yml"), any());
        verify(syncStatusRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("GIVEN a new deployment WHEN syncing THEN should assign repository correctly")
    void shouldAssignRepositoryToNewDeployment() {
        // Given
        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/owner/repo");
        repoConfig.setDatadogServiceName("test-service");
        repoConfig.setDeploymentWorkflowFileName("deploy.yml");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        List<GitHubWorkflowRunDto> workflowRuns = List.of(
                createWorkflowRun(1L, "sha1", "success", "main")
        );
        when(githubClient.getWorkflowRuns(eq("owner"), eq("repo"), eq("deploy.yml"), any())).thenReturn(workflowRuns);
        when(deploymentRepository.existsById(1L)).thenReturn(false);

        // When
        deploymentSyncService.syncDeployments();

        // Then
        verify(deploymentRepository).saveAll(deploymentCaptor.capture());
        List<Deployment> savedDeployments = deploymentCaptor.getValue();
        assertThat(savedDeployments).hasSize(1);

        Deployment savedDeployment = savedDeployments.get(0);
        assertThat(savedDeployment.getRepository()).isNotNull();
        assertThat(savedDeployment.getRepository()).isEqualTo(repoConfig);
        assertThat(savedDeployment.getServiceName()).isEqualTo("test-service");
    }

    @Test
    @DisplayName("GIVEN workflow filename changes WHEN syncing THEN should reset sync and capture all deployments")
    void shouldResetSyncWhenWorkflowChanges() {
        // Given: Initial workflow was "old-deploy.yml"
        String oldWorkflow = "old-deploy.yml";
        String newWorkflow = "deploy.yml";

        RepositoryConfig repo = new RepositoryConfig("https://github.com/owner/test-repo");
        repo.setDeploymentWorkflowFileName(newWorkflow); // Changed to new workflow
        repo.setLastSyncedWorkflowFile(oldWorkflow); // Was using old workflow
        repo.setDatadogServiceName("test-service");

        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo));

        // Create old sync status for old workflow (should be ignored)
        SyncStatus oldSyncStatus = new SyncStatus(
                "DEPLOYMENT_SYNC_test-repo_" + oldWorkflow,
                LocalDateTime.now().minusDays(1)
        );
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.of(oldSyncStatus));

        // Return deployment when queried with null lastRun (full sync)
        GitHubWorkflowRunDto deployment = createWorkflowRun(1L, "sha1", "success", "main");
        when(githubClient.getWorkflowRuns(eq("owner"), eq("test-repo"), eq(newWorkflow), isNull()))
                .thenReturn(List.of(deployment));
        when(deploymentRepository.existsById(1L)).thenReturn(false);

        // When
        deploymentSyncService.syncDeployments();

        // Then: Should update lastSyncedWorkflowFile
        verify(repositoryConfigRepository, atLeastOnce()).save(argThat(r ->
                newWorkflow.equals(r.getLastSyncedWorkflowFile())
        ));

        // Then: Should query with null lastRun (full sync)
        verify(githubClient).getWorkflowRuns(eq("owner"), eq("test-repo"), eq(newWorkflow), isNull());

        // Then: Should save deployment
        verify(deploymentRepository).saveAll(anyList());

        // Then: Should save sync status with new key format
        verify(syncStatusRepository).save(argThat(status ->
                status.getJobName().equals("DEPLOYMENT_SYNC_test-repo_" + newWorkflow)
        ));
    }

    @Test
    @DisplayName("GIVEN first time sync WHEN syncing THEN should set lastSyncedWorkflowFile")
    void shouldSetLastSyncedWorkflowOnFirstSync() {
        // Given
        String workflowFile = "deploy.yml";

        RepositoryConfig repo = new RepositoryConfig("https://github.com/owner/test-repo");
        repo.setDeploymentWorkflowFileName(workflowFile);
        repo.setLastSyncedWorkflowFile(null); // First time - never synced

        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
        when(githubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        // When
        deploymentSyncService.syncDeployments();

        // Then: Should set lastSyncedWorkflowFile
        verify(repositoryConfigRepository, atLeastOnce()).save(argThat(r ->
                workflowFile.equals(r.getLastSyncedWorkflowFile())
        ));

        // Then: Should query with null lastRun (full sync on first run)
        verify(githubClient).getWorkflowRuns(eq("owner"), eq("test-repo"), eq(workflowFile), isNull());
    }

    @Test
    @DisplayName("GIVEN same workflow filename WHEN syncing THEN should use existing sync status")
    void shouldUseExistingSyncStatusWhenWorkflowUnchanged() {
        // Given
        String workflowFile = "deploy.yml";
        LocalDateTime lastSync = LocalDateTime.now().minusHours(1);

        RepositoryConfig repo = new RepositoryConfig("https://github.com/owner/test-repo");
        repo.setDeploymentWorkflowFileName(workflowFile);
        repo.setLastSyncedWorkflowFile(workflowFile); // Same workflow - no change

        SyncStatus syncStatus = new SyncStatus(
                "DEPLOYMENT_SYNC_test-repo_" + workflowFile,
                lastSync
        );

        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo));
        when(syncStatusRepository.findById("DEPLOYMENT_SYNC_test-repo_" + workflowFile))
                .thenReturn(Optional.of(syncStatus));
        when(githubClient.getWorkflowRuns(any(), any(), any(), eq(lastSync)))
                .thenReturn(Collections.emptyList());

        // When
        deploymentSyncService.syncDeployments();

        // Then: Should use lastSync timestamp (incremental sync)
        verify(githubClient).getWorkflowRuns(eq("owner"), eq("test-repo"), eq(workflowFile), eq(lastSync));

        // Then: Should NOT update lastSyncedWorkflowFile (no change)
        verify(repositoryConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("GIVEN new sync key format WHEN updating status THEN should include workflow filename")
    void shouldIncludeWorkflowInSyncKey() {
        // Given
        String workflowFile = "deploy.yml";

        RepositoryConfig repo = new RepositoryConfig("https://github.com/owner/test-repo");
        repo.setDeploymentWorkflowFileName(workflowFile);
        repo.setLastSyncedWorkflowFile(workflowFile);

        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());
        when(githubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        // When
        deploymentSyncService.syncDeployments();

        // Then: Should save with new key format including workflow filename
        verify(syncStatusRepository).save(argThat(status ->
                status.getJobName().equals("DEPLOYMENT_SYNC_test-repo_" + workflowFile)
        ));
    }

    private GitHubWorkflowRunDto createWorkflowRun(Long id, String headSha, String conclusion, String branch) {
        GitHubWorkflowRunDto runDto = new GitHubWorkflowRunDto();
        runDto.setId(id);
        runDto.setName("deploy");
        runDto.setHeadBranch(branch);
        runDto.setHeadSha(headSha);
        runDto.setStatus("completed");
        runDto.setConclusion(conclusion);
        runDto.setCreatedAt(LocalDateTime.now());
        runDto.setUpdatedAt(LocalDateTime.now());
        return runDto;
    }
}
