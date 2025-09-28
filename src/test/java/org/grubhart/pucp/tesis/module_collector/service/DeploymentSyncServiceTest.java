package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_collector.github.GithubClientImpl;
import org.grubhart.pucp.tesis.module_domain.Deployment;
import org.grubhart.pucp.tesis.module_domain.DeploymentRepository;
import org.grubhart.pucp.tesis.module_domain.GitHubWorkflowRunDto;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfigRepository;
import org.grubhart.pucp.tesis.module_domain.SyncStatus;
import org.grubhart.pucp.tesis.module_domain.SyncStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentSyncServiceTest {

    @Mock
    private GithubClientImpl gitHubClient;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private SyncStatusRepository syncStatusRepository;

    @Mock
    private RepositoryConfigRepository repositoryConfigRepository;

    // El servicio bajo prueba. No usamos @InjectMocks para tener control sobre el constructor.
    private DeploymentSyncService deploymentSyncService;

    private final String WORKFLOW_FILENAME = "deploy.yml";

    @BeforeEach
    void setUp() {
        // Instanciamos el servicio manualmente con sus mocks
        deploymentSyncService = new DeploymentSyncService(
                gitHubClient,
                deploymentRepository,
                syncStatusRepository,
                repositoryConfigRepository,
                WORKFLOW_FILENAME
        );
    }

    @Test
    @DisplayName("GIVEN multiple repositories configured WHEN sync is called THEN sync deployments for each repository")
    void syncDeployments_forMultipleRepositories() {
        // GIVEN: Dos repositorios configurados en la base de datos
        RepositoryConfig repo1 = new RepositoryConfig("https://github.com/owner1/repo1");
        RepositoryConfig repo2 = new RepositoryConfig("https://github.com/owner2/repo2");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repo1, repo2));

        // GIVEN: Mock de la API de GitHub para cada repositorio
        GitHubWorkflowRunDto run1 = new GitHubWorkflowRunDto();
        run1.setId(101L);
        run1.setConclusion("success");
        when(gitHubClient.getWorkflowRuns(eq("owner1"), eq("repo1"), eq(WORKFLOW_FILENAME), any())).thenReturn(List.of(run1));

        GitHubWorkflowRunDto run2 = new GitHubWorkflowRunDto();
        run2.setId(201L);
        run2.setConclusion("success");
        when(gitHubClient.getWorkflowRuns(eq("owner2"), eq("repo2"), eq(WORKFLOW_FILENAME), any())).thenReturn(List.of(run2));

        // WHEN: Se ejecuta el servicio de sincronización
        deploymentSyncService.syncDeployments();

        // THEN: Se debe verificar que el cliente de GitHub fue llamado para ambos repositorios
        verify(gitHubClient, times(1)).getWorkflowRuns(eq("owner1"), eq("repo1"), eq(WORKFLOW_FILENAME), any());
        verify(gitHubClient, times(1)).getWorkflowRuns(eq("owner2"), eq("repo2"), eq(WORKFLOW_FILENAME), any());

        // THEN: Se debe guardar un deployment por cada repositorio
        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository, times(2)).save(deploymentCaptor.capture());
        List<Deployment> savedDeployments = deploymentCaptor.getAllValues();
        assertTrue(savedDeployments.stream().anyMatch(d -> d.getGithubId() == 101L));
        assertTrue(savedDeployments.stream().anyMatch(d -> d.getGithubId() == 201L));

        // THEN: Se debe actualizar el estado de sincronización para cada repositorio
        ArgumentCaptor<SyncStatus> syncStatusCaptor = ArgumentCaptor.forClass(SyncStatus.class);
        verify(syncStatusRepository, times(2)).save(syncStatusCaptor.capture());
        List<SyncStatus> savedStatuses = syncStatusCaptor.getAllValues();
        assertTrue(savedStatuses.stream().anyMatch(s -> s.getJobName().equals("DEPLOYMENT_SYNC_repo1")));
        assertTrue(savedStatuses.stream().anyMatch(s -> s.getJobName().equals("DEPLOYMENT_SYNC_repo2")));
    }

    @Test
    @DisplayName("GIVEN a repository with an invalid URL WHEN sync is called THEN it should skip it and continue")
    void syncDeployments_shouldSkipInvalidRepositoryUrl() {
        // GIVEN: Una configuración con una URL válida y una inválida
        RepositoryConfig validRepo = new RepositoryConfig("https://github.com/owner1/repo1");
        RepositoryConfig invalidRepo = new RepositoryConfig("invalid-url");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validRepo, invalidRepo));

        // GIVEN: Mock de la API solo para el repositorio válido
        GitHubWorkflowRunDto run1 = new GitHubWorkflowRunDto();
        run1.setId(101L);
        run1.setConclusion("success");
        when(gitHubClient.getWorkflowRuns(eq("owner1"), eq("repo1"), any(), any())).thenReturn(List.of(run1));

        // WHEN: Se ejecuta el servicio de sincronización
        deploymentSyncService.syncDeployments();

        // THEN: El cliente de GitHub solo debe ser llamado para el repositorio válido
        verify(gitHubClient, times(1)).getWorkflowRuns(eq("owner1"), eq("repo1"), any(), any());
        verify(gitHubClient, never()).getWorkflowRuns(eq("invalid-url"), any(), any(), any());

        // THEN: Solo se debe guardar el deployment del repositorio válido
        verify(deploymentRepository, times(1)).save(any(Deployment.class));
        verify(syncStatusRepository, times(1)).save(any(SyncStatus.class));
    }

    @Test
    @DisplayName("GIVEN no repositories configured WHEN sync is called THEN it should do nothing")
    void syncDeployments_whenNoRepositoriesConfigured() {
        // GIVEN: No hay repositorios en la base de datos
        when(repositoryConfigRepository.findAll()).thenReturn(List.of());

        // WHEN: Se ejecuta el servicio de sincronización
        deploymentSyncService.syncDeployments();

        // THEN: No se debe llamar a ningún servicio
        verifyNoInteractions(gitHubClient);
        verifyNoInteractions(deploymentRepository);
        verifyNoInteractions(syncStatusRepository);
    }

    @Test
    @DisplayName("GIVEN a repository with a null URL WHEN sync is called THEN it should be skipped")
    void syncDeployments_shouldSkipNullRepositoryUrl() {
        // GIVEN: Una configuración con una URL válida y una nula
        RepositoryConfig validRepo = new RepositoryConfig("https://github.com/owner1/repo1");
        RepositoryConfig nullUrlRepo = new RepositoryConfig(null);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validRepo, nullUrlRepo));

        // GIVEN: Mock de la API solo para el repositorio válido
        GitHubWorkflowRunDto run1 = new GitHubWorkflowRunDto();
        run1.setId(101L);
        run1.setConclusion("success");
        when(gitHubClient.getWorkflowRuns(eq("owner1"), eq("repo1"), any(), any())).thenReturn(List.of(run1));

        // WHEN: Se ejecuta el servicio de sincronización
        deploymentSyncService.syncDeployments();

        // THEN: El cliente de GitHub solo debe ser llamado para el repositorio válido
        verify(gitHubClient, times(1)).getWorkflowRuns(eq("owner1"), eq("repo1"), any(), any());

        // THEN: Solo se debe guardar el deployment del repositorio válido
        verify(deploymentRepository, times(1)).save(any(Deployment.class));
        verify(syncStatusRepository, times(1)).save(any(SyncStatus.class));
    }

    @Test
    @DisplayName("GIVEN a repository with an empty URL WHEN sync is called THEN it should be skipped")
    void syncDeployments_shouldSkipEmptyRepositoryUrl() {
        // GIVEN: Una configuración con una URL válida y una vacía
        RepositoryConfig validRepo = new RepositoryConfig("https://github.com/owner1/repo1");
        RepositoryConfig emptyUrlRepo = new RepositoryConfig("");
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validRepo, emptyUrlRepo));

        // GIVEN: Mock de la API solo para el repositorio válido
        GitHubWorkflowRunDto run1 = new GitHubWorkflowRunDto();
        run1.setId(101L);
        run1.setConclusion("success");
        when(gitHubClient.getWorkflowRuns(eq("owner1"), eq("repo1"), any(), any())).thenReturn(List.of(run1));

        // WHEN: Se ejecuta el servicio de sincronización
        deploymentSyncService.syncDeployments();

        // THEN: El cliente de GitHub solo debe ser llamado para el repositorio válido
        verify(gitHubClient, times(1)).getWorkflowRuns(eq("owner1"), eq("repo1"), any(), any());

        // THEN: Solo se debe guardar el deployment del repositorio válido
        verify(deploymentRepository, times(1)).save(any(Deployment.class));
        verify(syncStatusRepository, times(1)).save(any(SyncStatus.class));
    }
}
