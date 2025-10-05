package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadTimeCalculationServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private ChangeLeadTimeRepository changeLeadTimeRepository;

    @Captor
    private ArgumentCaptor<List<ChangeLeadTime>> changeLeadTimeCaptor;

    private LeadTimeCalculationService service;

    @BeforeEach
    void setUp() {
        service = new LeadTimeCalculationService(deploymentRepository, commitRepository, changeLeadTimeRepository);
    }

    @Test
    void shouldProcessCommitsCorrectly_WhenMergeCommitsArePresent() {
        // Arrange: Create a complex commit history with a feature branch and merge
        LocalDateTime now = LocalDateTime.now();

        // Ancient History (before previous deployment)
        Commit ancientCommit = new Commit("sha-ancient", "author", "ancient", now.minusDays(10));

        // Previous Deployment History (main branch)
        Commit prevDeployCommit = new Commit("sha-prev-deploy", "author", "prev deploy", now.minusDays(5));
        prevDeployCommit.setParents(Collections.singletonList(ancientCommit));

        // Feature Branch
        Commit featureCommitA = new Commit("sha-feature-A", "author", "feat: A", now.minusDays(4));
        featureCommitA.setParents(Collections.singletonList(prevDeployCommit));
        Commit featureCommitB = new Commit("sha-feature-B", "author", "feat: B", now.minusDays(3));
        featureCommitB.setParents(Collections.singletonList(featureCommitA));

        // Main Branch evolution
        Commit mainCommit = new Commit("sha-main", "author", "fix on main", now.minusDays(2));
        mainCommit.setParents(Collections.singletonList(prevDeployCommit));

        // Merge Commit (current deployment)
        Commit currentDeployCommit = new Commit("sha-current-deploy", "author", "Merge branch 'feature'", now.minusDays(1));
        currentDeployCommit.setParents(Arrays.asList(mainCommit, featureCommitB)); // Merges main and feature

        // --- Set up Mocks ---
        Deployment previousDeployment = new Deployment();
        previousDeployment.setSha("sha-prev-deploy");
        Deployment currentDeployment = new Deployment();
        currentDeployment.setSha("sha-current-deploy");
        currentDeployment.setCreatedAt(now);

        when(deploymentRepository.findByLeadTimeProcessedFalseAndEnvironment(eq("production"), any(Sort.class)))
                .thenReturn(Collections.singletonList(currentDeployment));
        when(deploymentRepository.findFirstByEnvironmentAndCreatedAtBefore(anyString(), any(LocalDateTime.class), any(Sort.class)))
                .thenReturn(Optional.of(previousDeployment));

        // Mock the commit repository to return commits by SHA
        Map<String, Commit> commitMap = Arrays.asList(ancientCommit, prevDeployCommit, featureCommitA, featureCommitB, mainCommit, currentDeployCommit)
                .stream().collect(Collectors.toMap(Commit::getSha, Function.identity()));
        when(commitRepository.findBySha(anyString())).thenAnswer(invocation -> {
            String sha = invocation.getArgument(0);
            return Optional.ofNullable(commitMap.get(sha));
        });

        // Act
        service.calculate();

        // Assert
        // 1. Verify ChangeLeadTime calculation
        verify(changeLeadTimeRepository).saveAll(changeLeadTimeCaptor.capture());
        List<ChangeLeadTime> savedLeadTimes = changeLeadTimeCaptor.getValue();
        List<String> processedCommitShas = savedLeadTimes.stream().map(clt -> clt.getCommit().getSha()).collect(Collectors.toList());

        // Should process all commits AFTER the previous deployment
        assertThat(processedCommitShas).containsExactlyInAnyOrder(
                "sha-current-deploy",
                "sha-main",
                "sha-feature-B",
                "sha-feature-A"
        );
        assertThat(processedCommitShas).doesNotContain("sha-prev-deploy", "sha-ancient");
        assertThat(savedLeadTimes).hasSize(4);

        // 2. Verify deployment is marked as processed
        ArgumentCaptor<List<Deployment>> processedDeploymentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(deploymentRepository).saveAll(processedDeploymentsCaptor.capture());
        assertThat(processedDeploymentsCaptor.getValue().get(0).isLeadTimeProcessed()).isTrue();
    }

    @Test
    void calculate_whenDeploymentCommitIsNotFound_shouldDoNothingAndMarkAsProcessed() {
        // GIVEN
        // 1. A deployment to process
        Deployment currentDeployment = new Deployment();
        currentDeployment.setSha("sha-non-existent");
        currentDeployment.setCreatedAt(LocalDateTime.now());
        currentDeployment.setLeadTimeProcessed(false);

        when(deploymentRepository.findByLeadTimeProcessedFalseAndEnvironment(eq("production"), any(Sort.class)))
                .thenReturn(Collections.singletonList(currentDeployment));

        // 2. No previous deployment (simplifies the test)
        when(deploymentRepository.findFirstByEnvironmentAndCreatedAtBefore(anyString(), any(LocalDateTime.class), any(Sort.class)))
                .thenReturn(Optional.empty());

        // 3. The commit for the current deployment is not found
        when(commitRepository.findBySha("sha-non-existent")).thenReturn(Optional.empty());

        // WHEN
        service.calculate();

        // THEN
        // 1. No lead times should be calculated or saved
        verify(changeLeadTimeRepository, never()).saveAll(any());

        // 2. The deployment should be marked as processed and saved
        ArgumentCaptor<List<Deployment>> processedDeploymentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(deploymentRepository).saveAll(processedDeploymentsCaptor.capture());
        List<Deployment> savedDeployments = processedDeploymentsCaptor.getValue();
        assertThat(savedDeployments).hasSize(1);
        assertThat(savedDeployments.get(0).isLeadTimeProcessed()).isTrue();
        assertThat(savedDeployments.get(0).getSha()).isEqualTo("sha-non-existent");
    }

    @Test
    void calculate_whenNoPreviousProcessedDeployments_shouldProcessFromBeginning() {
        // Arrange: A simple commit history
        LocalDateTime now = LocalDateTime.now();

        Commit commitA = new Commit("sha-A", "author", "feat: A", now.minusDays(3));
        Commit commitB = new Commit("sha-B", "author", "feat: B", now.minusDays(2));
        commitB.setParents(Collections.singletonList(commitA));
        Commit currentDeployCommit = new Commit("sha-current-deploy", "author", "Deploy", now.minusDays(1));
        currentDeployCommit.setParents(Collections.singletonList(commitB));

        // --- Set up Mocks ---
        Deployment currentDeployment = new Deployment();
        currentDeployment.setSha("sha-current-deploy");
        currentDeployment.setCreatedAt(now);

        // 1. A deployment to process
        when(deploymentRepository.findByLeadTimeProcessedFalseAndEnvironment(eq("production"), any(Sort.class)))
                .thenReturn(Collections.singletonList(currentDeployment));

        // 2. KEY: No previous deployment is found
        when(deploymentRepository.findFirstByEnvironmentAndCreatedAtBefore(anyString(), any(LocalDateTime.class), any(Sort.class)))
                .thenReturn(Optional.empty());

        // Mock the commit repository to return commits by SHA
        Map<String, Commit> commitMap = Arrays.asList(commitA, commitB, currentDeployCommit)
                .stream().collect(Collectors.toMap(Commit::getSha, Function.identity()));
        when(commitRepository.findBySha(anyString())).thenAnswer(invocation -> {
            String sha = invocation.getArgument(0);
            return Optional.ofNullable(commitMap.get(sha));
        });

        // Act
        service.calculate();

        // Assert
        // 1. Verify ChangeLeadTime calculation
        verify(changeLeadTimeRepository).saveAll(changeLeadTimeCaptor.capture());
        List<ChangeLeadTime> savedLeadTimes = changeLeadTimeCaptor.getValue();
        List<String> processedCommitShas = savedLeadTimes.stream().map(clt -> clt.getCommit().getSha()).collect(Collectors.toList());

        // Should process all commits as there's no previous boundary
        assertThat(processedCommitShas).containsExactlyInAnyOrder(
                "sha-current-deploy",
                "sha-B",
                "sha-A"
        );
        assertThat(savedLeadTimes).hasSize(3);

        // 2. Verify deployment is marked as processed
        ArgumentCaptor<List<Deployment>> processedDeploymentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(deploymentRepository).saveAll(processedDeploymentsCaptor.capture());
        assertThat(processedDeploymentsCaptor.getValue().get(0).isLeadTimeProcessed()).isTrue();
    }

    @Test
    void calculate_whenNoUnprocessedDeployments_shouldDoNothing() {
        // GIVEN
        // The repository returns no deployments to process
        when(deploymentRepository.findByLeadTimeProcessedFalseAndEnvironment(eq("production"), any(Sort.class)))
                .thenReturn(Collections.emptyList());

        // WHEN
        service.calculate();

        // THEN
        // No attempt should be made to save any deployments (as none were processed)
        verify(deploymentRepository, never()).saveAll(any());
        // No attempt should be made to save any lead times
        verify(changeLeadTimeRepository, never()).saveAll(any());
    }

    @Test
    void calculate_whenParentCommitIsNotFound_shouldProcessOnlyAvailableCommits() {
        // GIVEN: A commit history with a missing link
        LocalDateTime now = LocalDateTime.now();

        // Commit B is MISSING from the repository, Commit A is its parent but should not be reached
        Commit commitA = new Commit("sha-A", "author", "feat: A", now.minusDays(3));
        Commit commitC = new Commit("sha-C", "author", "feat: C", now.minusDays(1));
        // We create a "dummy" parent object for C that points to the missing sha
        commitC.setParents(Collections.singletonList(new Commit("sha-B", "author", "feat: B", now.minusDays(2))));

        // --- Set up Mocks ---
        Deployment currentDeployment = new Deployment();
        currentDeployment.setSha("sha-C");
        currentDeployment.setCreatedAt(now);

        // 1. A deployment to process
        when(deploymentRepository.findByLeadTimeProcessedFalseAndEnvironment(eq("production"), any(Sort.class)))
                .thenReturn(Collections.singletonList(currentDeployment));

        // 2. No previous deployment to simplify the boundary
        when(deploymentRepository.findFirstByEnvironmentAndCreatedAtBefore(anyString(), any(LocalDateTime.class), any(Sort.class)))
                .thenReturn(Optional.empty());

        // 3. KEY: Mock the repository to return C, but not its parent B
        when(commitRepository.findBySha("sha-C")).thenReturn(Optional.of(commitC));
        when(commitRepository.findBySha("sha-B")).thenReturn(Optional.empty()); // The "hole" in the history

        // WHEN
        service.calculate();

        // THEN
        // 1. Verify ChangeLeadTime calculation
        verify(changeLeadTimeRepository).saveAll(changeLeadTimeCaptor.capture());
        List<ChangeLeadTime> savedLeadTimes = changeLeadTimeCaptor.getValue();
        List<String> processedCommitShas = savedLeadTimes.stream().map(clt -> clt.getCommit().getSha()).collect(Collectors.toList());

        // Should process only the commits it could find before hitting the gap
        assertThat(processedCommitShas).containsExactlyInAnyOrder("sha-C");
        assertThat(savedLeadTimes).hasSize(1);

        // 2. Verify deployment is marked as processed
        ArgumentCaptor<List<Deployment>> processedDeploymentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(deploymentRepository).saveAll(processedDeploymentsCaptor.capture());
        assertThat(processedDeploymentsCaptor.getValue().get(0).isLeadTimeProcessed()).isTrue();
    }
}
