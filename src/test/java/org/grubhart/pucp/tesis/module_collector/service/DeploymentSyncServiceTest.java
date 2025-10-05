package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_collector.github.GithubClientImpl;
import org.grubhart.pucp.tesis.module_domain.*;
import org.grubhart.pucp.tesis.module_processor.LeadTimeCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullSource;


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
                leadTimeCalculationService,
                WORKFLOW_FILE_NAME
        );
    }

    private GitHubWorkflowRunDto createWorkflowRunDto(Long id, String name, String conclusion, String branch, String sha) {
        GitHubWorkflowRunDto dto = new GitHubWorkflowRunDto();
        dto.setId(id);
        dto.setName(name);
        dto.setHeadBranch(branch);
        dto.setHeadSha(sha);
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

        GitHubWorkflowRunDto newRun1 = createWorkflowRunDto(1L, "run1", "success", "main", "sha1");
        GitHubWorkflowRunDto newRun2 = createWorkflowRunDto(2L, "run2", "success", "develop", "sha2");
        when(githubClient.getWorkflowRuns("owner", "repo", WORKFLOW_FILE_NAME, null)).thenReturn(List.of(newRun1, newRun2));

        when(deploymentRepository.existsById(1L)).thenReturn(false);
        when(deploymentRepository.existsById(2L)).thenReturn(false);

        // Act
        deploymentSyncService.syncDeployments();

        // Assert
        verify(deploymentRepository).saveAll(deploymentsCaptor.capture());
        List<Deployment> savedDeployments = deploymentsCaptor.getValue();
        assertEquals(2, savedDeployments.size());

        Deployment productionDeployment = savedDeployments.stream().filter(d -> d.getGithubId() == 1L).findFirst().orElseThrow();
        assertEquals("production", productionDeployment.getEnvironment());
        assertEquals("sha1", productionDeployment.getSha());

        Deployment otherDeployment = savedDeployments.stream().filter(d -> d.getGithubId() == 2L).findFirst().orElseThrow();
        assertEquals(null, otherDeployment.getEnvironment());
        assertEquals("sha2", otherDeployment.getSha());

        verify(leadTimeCalculationService, times(1)).calculate();
    }

    @ParameterizedTest
    @ValueSource(strings = {"failure", "cancelled", "skipped", "timed_out"})
    @DisplayName("syncDeployments debe ignorar las ejecuciones de workflow que no fueron exitosas")
    void syncDeployments_whenRunConclusionIsNotSuccess_shouldNotSaveDeployment(String nonSuccessConclusion) {
        // Arrange
        RepositoryConfig repoConfig = new RepositoryConfig(VALID_REPO_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(any())).thenReturn(Optional.empty());

        GitHubWorkflowRunDto failedRun = createWorkflowRunDto(1L, "run1", nonSuccessConclusion, "main", "sha1");
        when(githubClient.getWorkflowRuns("owner", "repo", WORKFLOW_FILE_NAME, null)).thenReturn(List.of(failedRun));

        // Act
        deploymentSyncService.syncDeployments();

        // Assert
        verify(deploymentRepository, never()).saveAll(any());
        verify(leadTimeCalculationService, never()).calculate();
    }

    @Test
    void syncDeployments_shouldSkipRun_whenShaIsMissing() {
        // Arrange
        RepositoryConfig repoConfig = new RepositoryConfig(VALID_REPO_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));

        GitHubWorkflowRunDto invalidRun = createWorkflowRunDto(1L, "invalid_run", "success", "main", null); // SHA nulo
        GitHubWorkflowRunDto validRun = createWorkflowRunDto(2L, "valid_run", "success", "main", "sha2");
        when(githubClient.getWorkflowRuns(any(), any(), any(), any())).thenReturn(List.of(invalidRun, validRun));

        when(deploymentRepository.existsById(2L)).thenReturn(false);

        // Act
        deploymentSyncService.syncDeployments();

        // Assert
        verify(deploymentRepository).saveAll(deploymentsCaptor.capture());
        List<Deployment> savedDeployments = deploymentsCaptor.getValue();
        assertEquals(1, savedDeployments.size());
        assertEquals(2L, savedDeployments.get(0).getGithubId());
        assertEquals("sha2", savedDeployments.get(0).getSha());

        verify(leadTimeCalculationService, times(1)).calculate();
    }


    @Test
    void syncDeployments_whenApiReturnsMixedRuns_shouldOnlySaveNewOnes() {
        // Arrange
        RepositoryConfig repoConfig = new RepositoryConfig(VALID_REPO_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(any())).thenReturn(Optional.empty());

        GitHubWorkflowRunDto existingRun = createWorkflowRunDto(1L, "run1", "success", "main", "sha1");
        GitHubWorkflowRunDto newRun = createWorkflowRunDto(2L, "run2", "success", "main", "sha2");
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

        verify(leadTimeCalculationService, times(1)).calculate();
    }

    @Test
    void syncDeployments_whenApiReturnsNoNewRuns_shouldNotSaveAnything() {
        // Arrange
        RepositoryConfig repoConfig = new RepositoryConfig(VALID_REPO_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(any())).thenReturn(Optional.empty());

        GitHubWorkflowRunDto existingRun = createWorkflowRunDto(1L, "run1", "success", "main", "sha1");
        when(githubClient.getWorkflowRuns("owner", "repo", WORKFLOW_FILE_NAME, null)).thenReturn(List.of(existingRun));

        when(deploymentRepository.existsById(1L)).thenReturn(true);

        // Act
        deploymentSyncService.syncDeployments();

        // Assert
        verify(deploymentRepository, never()).saveAll(any());
        verify(leadTimeCalculationService, never()).calculate();
    }

    @Test
    @DisplayName("syncDeployments debe manejar un error inesperado durante la recolección y continuar")
    void syncDeployments_shouldHandleUnexpectedErrorAndContinue() {
        // 1. Arrange
        // Configuramos un repositorio válido.
        RepositoryConfig validConfig = new RepositoryConfig(VALID_REPO_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(validConfig));

        // La clave: Simulamos que el cliente de GitHub lanza una excepción inesperada.
        when(githubClient.getWorkflowRuns(anyString(), anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Error de red simulado"));

        // 2. Act
        // Ejecutamos el método principal. No debería lanzar una excepción hacia afuera,
        // ya que el try-catch interno debe manejarla.
        deploymentSyncService.syncDeployments();

        // 3. Assert
        // Verificamos que se intentó obtener los datos, lo que provocó el error.
        verify(githubClient, times(1)).getWorkflowRuns(eq("owner"), eq("repo"), anyString(), any());

        // Verificamos que, debido al error, NUNCA se intentó guardar deployments.
        verify(deploymentRepository, never()).saveAll(any());

        // Verificamos que NUNCA se actualizó el estado de la sincronización para este repo fallido.
        verify(syncStatusRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncDeployments debe saltar el repositorio si la URL de configuración es nula")
    void syncDeployments_shouldSkipRepository_whenUrlIsNull() {
        // 1. Arrange
        // Creamos una configuración de repositorio con una URL nula.
        // Usamos un mock para forzar este estado, ya que la entidad real podría no permitirlo.
        RepositoryConfig nullUrlConfig = mock(RepositoryConfig.class);
        when(nullUrlConfig.getRepositoryUrl()).thenReturn(null);

        // Configuramos el mock para que devuelva esta configuración inválida.
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(nullUrlConfig));

        // 2. Act
        // Ejecutamos el método principal.
        deploymentSyncService.syncDeployments();

        // 3. Assert
        // Verificamos que, debido a la URL nula, nunca se intentó recolectar datos.
        verify(githubClient, never()).getWorkflowRuns(any(), any(), any(), any());
        verify(deploymentRepository, never()).saveAll(any());
        verify(syncStatusRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "}) // Provee una cadena vacía y una con espacios
    @DisplayName("syncDeployments debe saltar el repositorio si la URL está vacía o en blanco")
    void syncDeployments_shouldSkipRepository_whenUrlIsEmptyOrBlank(String invalidUrl) {
        // 1. Arrange
        // Creamos una configuración con la URL inválida proporcionada por el test.
        RepositoryConfig emptyUrlConfig = new RepositoryConfig(invalidUrl);

        // Configuramos el mock para que devuelva esta configuración.
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(emptyUrlConfig));

        // 2. Act
        // Ejecutamos el método principal.
        deploymentSyncService.syncDeployments();

        // 3. Assert
        // Verificamos que, debido a la URL inválida, nunca se intentó recolectar datos.
        verify(githubClient, never()).getWorkflowRuns(any(), any(), any(), any());
        verify(deploymentRepository, never()).saveAll(any());
        verify(syncStatusRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("syncDeployments debe ignorar las ejecuciones con un headSha inválido (nulo, vacío o en blanco)")
    void syncDeployments_whenRunHasInvalidHeadSha_shouldNotSaveDeployment(String invalidSha) {
        // Arrange
        RepositoryConfig repoConfig = new RepositoryConfig(VALID_REPO_URL);
        when(repositoryConfigRepository.findAll()).thenReturn(List.of(repoConfig));
        when(syncStatusRepository.findById(any())).thenReturn(Optional.empty());

        GitHubWorkflowRunDto invalidRun = createWorkflowRunDto(1L, "run1", "success", "main", invalidSha);
        when(githubClient.getWorkflowRuns("owner", "repo", WORKFLOW_FILE_NAME, null)).thenReturn(List.of(invalidRun));

        // Act
        deploymentSyncService.syncDeployments();

        // Assert
        verify(deploymentRepository, never()).saveAll(any());
        verify(leadTimeCalculationService, never()).calculate();
    }

}
