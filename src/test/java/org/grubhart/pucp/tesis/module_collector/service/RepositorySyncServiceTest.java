package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.GithubRepositoryCollector;
import org.grubhart.pucp.tesis.module_domain.GithubRepositoryDto;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfigRepository;
import org.grubhart.pucp.tesis.module_domain.RepositorySyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepositorySyncService - Sincronización de Repositorios")
class RepositorySyncServiceTest {

    @Mock
    private GithubRepositoryCollector githubRepositoryCollector;

    @Mock
    private RepositoryConfigRepository repositoryConfigRepository;

    @Captor
    private ArgumentCaptor<List<RepositoryConfig>> repositoryListCaptor;

    private RepositorySyncService repositorySyncService;

    @BeforeEach
    void setUp() {
        repositorySyncService = new RepositorySyncService(
                githubRepositoryCollector,
                repositoryConfigRepository
        );
    }

    @Test
    @DisplayName("GIVEN new repositories from GitHub WHEN synchronizing THEN should create new RepositoryConfig records")
    void shouldCreateNewRepositories() {
        // Given
        List<GithubRepositoryDto> githubRepos = List.of(
                new GithubRepositoryDto(1L, "repo1", "user/repo1", "https://github.com/user/repo1", false,
                        new GithubRepositoryDto.Owner("user")),
                new GithubRepositoryDto(2L, "repo2", "user/repo2", "https://github.com/user/repo2", false,
                        new GithubRepositoryDto.Owner("user"))
        );
        when(githubRepositoryCollector.getUserRepositories()).thenReturn(githubRepos);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of());

        // When
        RepositorySyncResult result = repositorySyncService.synchronizeRepositories();

        // Then
        verify(repositoryConfigRepository).saveAll(repositoryListCaptor.capture());
        List<RepositoryConfig> savedRepos = repositoryListCaptor.getValue();

        assertThat(savedRepos).hasSize(2);
        assertThat(savedRepos.get(0).getRepositoryUrl()).isEqualTo("https://github.com/user/repo1");
        assertThat(savedRepos.get(0).getDatadogServiceName()).isNull();
        assertThat(savedRepos.get(1).getRepositoryUrl()).isEqualTo("https://github.com/user/repo2");
        assertThat(savedRepos.get(1).getDatadogServiceName()).isNull();

        assertThat(result.newRepositories()).isEqualTo(2);
        assertThat(result.totalRepositories()).isEqualTo(2);
        assertThat(result.unchanged()).isEqualTo(0);
    }

    @Test
    @DisplayName("GIVEN existing repositories WHEN synchronizing THEN should be idempotent and not duplicate")
    void shouldBeIdempotent() {
        // Given
        RepositoryConfig existingRepo = new RepositoryConfig("https://github.com/user/repo1", "my-service");

        List<GithubRepositoryDto> githubRepos = List.of(
                new GithubRepositoryDto(1L, "repo1", "user/repo1", "https://github.com/user/repo1", false,
                        new GithubRepositoryDto.Owner("user"))
        );

        when(githubRepositoryCollector.getUserRepositories()).thenReturn(githubRepos);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(existingRepo));

        // When
        RepositorySyncResult result = repositorySyncService.synchronizeRepositories();

        // Then
        verify(repositoryConfigRepository, never()).saveAll(anyList());

        assertThat(result.newRepositories()).isEqualTo(0);
        assertThat(result.totalRepositories()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(1);
    }

    @Test
    @DisplayName("GIVEN existing repository with service name WHEN synchronizing THEN should not modify service name")
    void shouldNotModifyExistingServiceName() {
        // Given
        RepositoryConfig existingRepo = new RepositoryConfig("https://github.com/user/repo1", "my-service");

        List<GithubRepositoryDto> githubRepos = List.of(
                new GithubRepositoryDto(1L, "repo1", "user/repo1", "https://github.com/user/repo1", false,
                        new GithubRepositoryDto.Owner("user"))
        );

        when(githubRepositoryCollector.getUserRepositories()).thenReturn(githubRepos);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(existingRepo));

        // When
        repositorySyncService.synchronizeRepositories();

        // Then
        verify(repositoryConfigRepository, never()).saveAll(anyList());
        assertThat(existingRepo.getDatadogServiceName()).isEqualTo("my-service");
    }

    @Test
    @DisplayName("GIVEN mixed new and existing repositories WHEN synchronizing THEN should only create new ones")
    void shouldHandleMixedRepositories() {
        // Given
        RepositoryConfig existingRepo = new RepositoryConfig("https://github.com/user/repo1", null);

        List<GithubRepositoryDto> githubRepos = List.of(
                new GithubRepositoryDto(1L, "repo1", "user/repo1", "https://github.com/user/repo1", false,
                        new GithubRepositoryDto.Owner("user")),
                new GithubRepositoryDto(2L, "repo2", "user/repo2", "https://github.com/user/repo2", false,
                        new GithubRepositoryDto.Owner("user"))
        );

        when(githubRepositoryCollector.getUserRepositories()).thenReturn(githubRepos);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(existingRepo));

        // When
        RepositorySyncResult result = repositorySyncService.synchronizeRepositories();

        // Then
        verify(repositoryConfigRepository).saveAll(repositoryListCaptor.capture());
        List<RepositoryConfig> savedRepos = repositoryListCaptor.getValue();

        assertThat(savedRepos).hasSize(1);
        assertThat(savedRepos.get(0).getRepositoryUrl()).isEqualTo("https://github.com/user/repo2");
        assertThat(savedRepos.get(0).getDatadogServiceName()).isNull();

        assertThat(result.newRepositories()).isEqualTo(1);
        assertThat(result.totalRepositories()).isEqualTo(2);
        assertThat(result.unchanged()).isEqualTo(1);
    }

    @Test
    @DisplayName("GIVEN repository no longer in GitHub WHEN synchronizing THEN should not delete it from database")
    void shouldNotDeleteRepositories() {
        // Given
        RepositoryConfig existingRepo1 = new RepositoryConfig("https://github.com/user/repo1", "service1");
        RepositoryConfig existingRepo2 = new RepositoryConfig("https://github.com/user/repo2", "service2");

        // GitHub only returns repo1, repo2 is missing
        List<GithubRepositoryDto> githubRepos = List.of(
                new GithubRepositoryDto(1L, "repo1", "user/repo1", "https://github.com/user/repo1", false,
                        new GithubRepositoryDto.Owner("user"))
        );

        when(githubRepositoryCollector.getUserRepositories()).thenReturn(githubRepos);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(existingRepo1, existingRepo2));

        // When
        RepositorySyncResult result = repositorySyncService.synchronizeRepositories();

        // Then
        verify(repositoryConfigRepository, never()).delete(any());
        verify(repositoryConfigRepository, never()).deleteAll(anyList());
        verify(repositoryConfigRepository, never()).deleteById(any());

        assertThat(result.newRepositories()).isEqualTo(0);
        assertThat(result.totalRepositories()).isEqualTo(2); // Still counts both in DB
        assertThat(result.unchanged()).isEqualTo(1); // Only repo1 is unchanged
    }

    @Test
    @DisplayName("GIVEN empty GitHub repositories WHEN synchronizing THEN should not create or delete anything")
    void shouldHandleEmptyGithubRepos() {
        // Given
        RepositoryConfig existingRepo = new RepositoryConfig("https://github.com/user/repo1", null);

        when(githubRepositoryCollector.getUserRepositories()).thenReturn(List.of());
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(existingRepo));

        // When
        RepositorySyncResult result = repositorySyncService.synchronizeRepositories();

        // Then
        verify(repositoryConfigRepository, never()).saveAll(anyList());
        verify(repositoryConfigRepository, never()).delete(any());

        assertThat(result.newRepositories()).isEqualTo(0);
        assertThat(result.totalRepositories()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(0);
    }

    @Test
    @DisplayName("GIVEN only existing repositories WHEN synchronizing THEN should correctly count unchanged repositories")
    void shouldCorrectlyCountUnchangedRepositories() {
        // Given
        RepositoryConfig existingRepo1 = new RepositoryConfig("https://github.com/user/repo1", "service1");
        RepositoryConfig existingRepo2 = new RepositoryConfig("https://github.com/user/repo2", "service2");

        List<GithubRepositoryDto> githubRepos = List.of(
                new GithubRepositoryDto(1L, "repo1", "user/repo1", "https://github.com/user/repo1", false,
                        new GithubRepositoryDto.Owner("user")),
                new GithubRepositoryDto(2L, "repo2", "user/repo2", "https://github.com/user/repo2", false,
                        new GithubRepositoryDto.Owner("user"))
        );

        when(githubRepositoryCollector.getUserRepositories()).thenReturn(githubRepos);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(existingRepo1, existingRepo2));

        // When
        RepositorySyncResult result = repositorySyncService.synchronizeRepositories();

        // Then
        verify(repositoryConfigRepository, never()).saveAll(anyList());

        assertThat(result.newRepositories()).isEqualTo(0);
        assertThat(result.totalRepositories()).isEqualTo(2);
        assertThat(result.unchanged()).isEqualTo(2); // Verify unchanged count
    }
}