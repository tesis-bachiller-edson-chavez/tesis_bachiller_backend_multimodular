package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_api.dto.RepositoryDto;
import org.grubhart.pucp.tesis.module_api.dto.RepositorySyncResultDto;
import org.grubhart.pucp.tesis.module_api.dto.UpdateRepositoryRequest;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfigRepository;
import org.grubhart.pucp.tesis.module_domain.RepositorySyncResult;
import org.grubhart.pucp.tesis.module_domain.RepositorySyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RepositoryController - API de gestión de repositorios")
class RepositoryControllerTest {

    @Mock
    private RepositoryConfigRepository repositoryConfigRepository;

    @Mock
    private RepositorySyncService repositorySyncService;

    @InjectMocks
    private RepositoryController repositoryController;

    private RepositoryConfig repo1;
    private RepositoryConfig repo2;

    @BeforeEach
    void setUp() {
        repo1 = new RepositoryConfig("https://github.com/user/repo1", "service1");
        repo2 = new RepositoryConfig("https://github.com/user/repo2", null);
    }

    @Test
    @DisplayName("GET /repositories - Should return all repositories")
    void getAllRepositories_shouldReturnAllRepositories() {
        // Given
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo1, repo2));

        // When
        List<RepositoryDto> result = repositoryController.getAllRepositories();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).repositoryUrl()).isEqualTo("https://github.com/user/repo1");
        assertThat(result.get(0).datadogServiceName()).isEqualTo("service1");
        assertThat(result.get(0).owner()).isEqualTo("user");
        assertThat(result.get(0).repoName()).isEqualTo("repo1");

        assertThat(result.get(1).repositoryUrl()).isEqualTo("https://github.com/user/repo2");
        assertThat(result.get(1).datadogServiceName()).isNull();
        assertThat(result.get(1).owner()).isEqualTo("user");
        assertThat(result.get(1).repoName()).isEqualTo("repo2");

        verify(repositoryConfigRepository).findAll();
    }

    @Test
    @DisplayName("GET /repositories - Should return empty list when no repositories exist")
    void getAllRepositories_whenEmpty_shouldReturnEmptyList() {
        // Given
        when(repositoryConfigRepository.findAll()).thenReturn(List.of());

        // When
        List<RepositoryDto> result = repositoryController.getAllRepositories();

        // Then
        assertThat(result).isEmpty();
        verify(repositoryConfigRepository).findAll();
    }

    @Test
    @DisplayName("POST /repositories/sync - Should call sync service and return result")
    void syncRepositories_shouldCallServiceAndReturnResult() {
        // Given
        RepositorySyncResult syncResult = new RepositorySyncResult(3, 10, 7);
        when(repositorySyncService.synchronizeRepositories()).thenReturn(syncResult);

        // When
        ResponseEntity<RepositorySyncResultDto> response = repositoryController.syncRepositories();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().newRepositories()).isEqualTo(3);
        assertThat(response.getBody().totalRepositories()).isEqualTo(10);
        assertThat(response.getBody().unchanged()).isEqualTo(7);

        verify(repositorySyncService).synchronizeRepositories();
    }

    @Test
    @DisplayName("POST /repositories/sync - Should handle sync errors gracefully")
    void syncRepositories_whenServiceThrowsException_shouldReturnInternalServerError() {
        // Given
        when(repositorySyncService.synchronizeRepositories())
                .thenThrow(new RuntimeException("GitHub API error"));

        // When
        ResponseEntity<RepositorySyncResultDto> response = repositoryController.syncRepositories();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();

        verify(repositorySyncService).synchronizeRepositories();
    }

    @Test
    @DisplayName("PUT /repositories/{id} - Should update repository and return updated data")
    void updateRepository_whenRepositoryExists_shouldUpdateAndReturn() {
        // Given
        Long repoId = 1L;
        UpdateRepositoryRequest request = new UpdateRepositoryRequest("new-service-name", "deploy.yml");

        RepositoryConfig existingRepo = new RepositoryConfig("https://github.com/user/repo1", "old-service");
        when(repositoryConfigRepository.findById(repoId)).thenReturn(Optional.of(existingRepo));
        when(repositoryConfigRepository.save(any(RepositoryConfig.class))).thenReturn(existingRepo);

        // When
        ResponseEntity<RepositoryDto> response = repositoryController.updateRepository(repoId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().repositoryUrl()).isEqualTo("https://github.com/user/repo1");
        assertThat(response.getBody().datadogServiceName()).isEqualTo("new-service-name");
        assertThat(response.getBody().deploymentWorkflowFileName()).isEqualTo("deploy.yml");

        verify(repositoryConfigRepository).findById(repoId);
        verify(repositoryConfigRepository).save(existingRepo);
        assertThat(existingRepo.getDatadogServiceName()).isEqualTo("new-service-name");
        assertThat(existingRepo.getDeploymentWorkflowFileName()).isEqualTo("deploy.yml");
    }

    @Test
    @DisplayName("PUT /repositories/{id} - Should allow setting null to remove service association")
    void updateRepository_whenSettingNull_shouldAllowIt() {
        // Given
        Long repoId = 1L;
        UpdateRepositoryRequest request = new UpdateRepositoryRequest(null, null);

        RepositoryConfig existingRepo = new RepositoryConfig("https://github.com/user/repo1", "service1");
        when(repositoryConfigRepository.findById(repoId)).thenReturn(Optional.of(existingRepo));
        when(repositoryConfigRepository.save(any(RepositoryConfig.class))).thenReturn(existingRepo);

        // When
        ResponseEntity<RepositoryDto> response = repositoryController.updateRepository(repoId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().datadogServiceName()).isNull();
        assertThat(response.getBody().deploymentWorkflowFileName()).isNull();

        verify(repositoryConfigRepository).save(existingRepo);
        assertThat(existingRepo.getDatadogServiceName()).isNull();
        assertThat(existingRepo.getDeploymentWorkflowFileName()).isNull();
    }

    @Test
    @DisplayName("PUT /repositories/{id} - Should return 404 when repository not found")
    void updateRepository_whenRepositoryNotFound_shouldReturn404() {
        // Given
        Long repoId = 999L;
        UpdateRepositoryRequest request = new UpdateRepositoryRequest("service-name", "deploy.yml");

        when(repositoryConfigRepository.findById(repoId)).thenReturn(Optional.empty());

        // When
        ResponseEntity<RepositoryDto> response = repositoryController.updateRepository(repoId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();

        verify(repositoryConfigRepository).findById(repoId);
        verify(repositoryConfigRepository, never()).save(any());
    }

    @Test
    @DisplayName("PUT /repositories/{id} - Should handle update errors gracefully")
    void updateRepository_whenSaveThrowsException_shouldReturnInternalServerError() {
        // Given
        Long repoId = 1L;
        UpdateRepositoryRequest request = new UpdateRepositoryRequest("new-service", "manual-deploy.yml");

        RepositoryConfig existingRepo = new RepositoryConfig("https://github.com/user/repo1", "old-service");
        when(repositoryConfigRepository.findById(repoId)).thenReturn(Optional.of(existingRepo));
        when(repositoryConfigRepository.save(any(RepositoryConfig.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When
        ResponseEntity<RepositoryDto> response = repositoryController.updateRepository(repoId, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNull();

        verify(repositoryConfigRepository).findById(repoId);
        verify(repositoryConfigRepository).save(any());
    }
}