package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_collector.github.GithubClientImpl;
import org.grubhart.pucp.tesis.module_domain.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
class PullRequestSyncServiceTest {

    @Autowired
    private PullRequestSyncService pullRequestSyncService;

    @MockitoBean
    private PullRequestRepository pullRequestRepository;

    @MockitoBean
    private SyncStatusRepository syncStatusRepository;

    @MockitoBean
    private RepositoryConfigRepository repositoryConfigRepository;

    @MockitoBean
    private GithubClientImpl githubClient; // Mockeamos la clase concreta

    @Test
    void shouldNotCallCollectorWhenNoRepositoriesAreConfigured() {
        when(repositoryConfigRepository.findAll()).thenReturn(Collections.emptyList());
        pullRequestSyncService.syncPullRequests();
        verify(githubClient, never()).getPullRequests(anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void shouldFetchPullRequestsForConfiguredRepository() {
        RepositoryConfig config = new RepositoryConfig("https://github.com/test-owner/test-repo");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(config));
        when(syncStatusRepository.findById(eq("PULL_REQUEST_SYNC_test-owner/test-repo"))).thenReturn(Optional.empty());
        pullRequestSyncService.syncPullRequests();
        verify(githubClient).getPullRequests(eq("test-owner"), eq("test-repo"), any(LocalDateTime.class));
    }

    @Test
    void shouldSaveNewPullRequestsAndIgnoreExistingOnes() {
        RepositoryConfig config = new RepositoryConfig("https://github.com/test-owner/test-repo");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(config));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        GithubPullRequestDto existingDto = new GithubPullRequestDto();
        existingDto.setId(123L);
        GithubPullRequestDto newDto = new GithubPullRequestDto();
        newDto.setId(456L);
        when(githubClient.getPullRequests(anyString(), anyString(), any())).thenReturn(List.of(existingDto, newDto));

        PullRequest existingPullRequest = new PullRequest();
        existingPullRequest.setId(123L);
        when(pullRequestRepository.findAllById(Set.of(123L, 456L))).thenReturn(List.of(existingPullRequest));

        pullRequestSyncService.syncPullRequests();

        ArgumentCaptor<List<PullRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(pullRequestRepository).saveAll(captor.capture());

        List<PullRequest> savedPullRequests = captor.getValue();
        assertThat(savedPullRequests).hasSize(1);
        assertThat(savedPullRequests.get(0).getId()).isEqualTo(456L);
    }

    @Test
    void shouldFetchAndSetFirstCommitShaForNewPullRequests() {
        // 1. Setup: Configurar un repositorio y un DTO de PR enriquecido, como lo devolvería el colector.
        RepositoryConfig config = new RepositoryConfig("https://github.com/test-owner/test-repo");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(config));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        String expectedFirstCommitSha = "sha-of-first-commit";
        GithubPullRequestDto newEnrichedDto = new GithubPullRequestDto();
        newEnrichedDto.setId(456L);
        newEnrichedDto.setNumber(101);
        newEnrichedDto.setFirstCommitSha(expectedFirstCommitSha); // El DTO ya viene con el SHA

        // Simular que el colector devuelve el DTO enriquecido
        when(githubClient.getPullRequests(eq("test-owner"), eq("test-repo"), any())).thenReturn(List.of(newEnrichedDto));

        // Simular que el PR no existe en la BD
        when(pullRequestRepository.findAllById(Set.of(456L))).thenReturn(Collections.emptyList());

        // 2. Ejecutar el servicio
        pullRequestSyncService.syncPullRequests();

        // 3. Verificar: Capturar lo que se guarda y comprobar que el SHA se mapeó a la entidad
        ArgumentCaptor<List<PullRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(pullRequestRepository).saveAll(captor.capture());

        List<PullRequest> savedPullRequests = captor.getValue();
        assertThat(savedPullRequests).hasSize(1);
        PullRequest savedPr = savedPullRequests.get(0);

        assertThat(savedPr.getId()).isEqualTo(456L);
        assertThat(savedPr.getFirstCommitSha()).isEqualTo(expectedFirstCommitSha); // Esta es la aserción clave
    }

    @Test
    void shouldNotSaveWhenAllPullRequestsAlreadyExist() {
        RepositoryConfig config = new RepositoryConfig("https://github.com/test-owner/test-repo");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(config));
        when(syncStatusRepository.findById(anyString())).thenReturn(Optional.empty());

        GithubPullRequestDto existingDto = new GithubPullRequestDto();
        existingDto.setId(123L);
        when(githubClient.getPullRequests(anyString(), anyString(), any())).thenReturn(List.of(existingDto));

        PullRequest existingPullRequest = new PullRequest();
        existingPullRequest.setId(123L);
        when(pullRequestRepository.findAllById(Set.of(123L))).thenReturn(List.of(existingPullRequest));

        pullRequestSyncService.syncPullRequests();

        verify(pullRequestRepository, never()).saveAll(any());
        verify(syncStatusRepository, times(1)).save(any()); // El estado sí debe actualizarse
    }

    @Test
    void shouldUpdateSyncStatusAfterSuccessfulSync() {
        RepositoryConfig config = new RepositoryConfig("https://github.com/test-owner/test-repo");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(config));
        when(githubClient.getPullRequests(anyString(), anyString(), any())).thenReturn(Collections.emptyList());

        pullRequestSyncService.syncPullRequests();

        ArgumentCaptor<SyncStatus> captor = ArgumentCaptor.forClass(SyncStatus.class);
        verify(syncStatusRepository).save(captor.capture());

        SyncStatus savedStatus = captor.getValue();
        assertThat(savedStatus.getJobName()).isEqualTo("PULL_REQUEST_SYNC_test-owner/test-repo");
        assertThat(savedStatus.getLastSuccessfulRun()).isCloseTo(LocalDateTime.now(), org.assertj.core.api.Assertions.within(java.time.temporal.ChronoUnit.SECONDS.getDuration()));
    }

    @Test
    void shouldSkipSyncWhenOwnerIsNull() {
        RepositoryConfig invalidConfig = new RepositoryConfig("http://invalid-url");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(invalidConfig));
        pullRequestSyncService.syncPullRequests();
        verify(githubClient, never()).getPullRequests(any(), any(), any());
        verify(syncStatusRepository, never()).save(any());
    }

    @Test
    void shouldSkipSyncWhenRepoNameIsNull() {
        RepositoryConfig invalidConfig = new RepositoryConfig("https://github.com/test-owner/ ");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(invalidConfig));
        pullRequestSyncService.syncPullRequests();
        verify(githubClient, never()).getPullRequests(any(), any(), any());
        verify(syncStatusRepository, never()).save(any());
    }

    @Test
    void shouldHandleApiErrorsGracefully() {
        RepositoryConfig config = new RepositoryConfig("https://github.com/test-owner/test-repo");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(config));
        when(githubClient.getPullRequests(anyString(), anyString(), any())).thenThrow(new RuntimeException("API Error"));
        assertDoesNotThrow(() -> pullRequestSyncService.syncPullRequests());
        verify(pullRequestRepository, never()).saveAll(any());
        verify(syncStatusRepository, never()).save(any());
    }

    @Test
    void shouldSyncForAllConfiguredRepositories() {
        // GIVEN
        RepositoryConfig repo1 = new RepositoryConfig("https://github.com/owner1/repo1");
        RepositoryConfig repo2 = new RepositoryConfig("https://github.com/owner2/repo2");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo1, repo2));

        // WHEN
        pullRequestSyncService.syncPullRequests();

        // THEN
        verify(githubClient, times(1)).getPullRequests(eq("owner1"), eq("repo1"), any());
        verify(githubClient, times(1)).getPullRequests(eq("owner2"), eq("repo2"), any());
        verify(syncStatusRepository, times(2)).save(any());
    }
}
