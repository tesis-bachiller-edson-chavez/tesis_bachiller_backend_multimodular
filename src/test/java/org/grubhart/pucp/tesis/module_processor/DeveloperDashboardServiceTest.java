package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.ChangeLeadTime;
import org.grubhart.pucp.tesis.module_domain.ChangeLeadTimeRepository;
import org.grubhart.pucp.tesis.module_domain.Commit;
import org.grubhart.pucp.tesis.module_domain.CommitParentRepository;
import org.grubhart.pucp.tesis.module_domain.CommitRepository;
import org.grubhart.pucp.tesis.module_domain.Deployment;
import org.grubhart.pucp.tesis.module_domain.IncidentRepository;
import org.grubhart.pucp.tesis.module_domain.PullRequestRepository;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperDashboardServiceTest {

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private ChangeLeadTimeRepository changeLeadTimeRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private PullRequestRepository pullRequestRepository;

    @Mock
    private CommitParentRepository commitParentRepository;

    @InjectMocks
    private DeveloperDashboardService developerDashboardService;

    @Test
    void testGetDeveloperMetrics_withCommits_returnsMetrics() {
        // GIVEN: Un developer con commits en dos repositorios
        String githubUsername = "john_doe";

        RepositoryConfig repo1 = new RepositoryConfig("https://github.com/org/repo1");
        RepositoryConfig repo2 = new RepositoryConfig("https://github.com/org/repo2");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);

        List<Commit> mockCommits = List.of(
                new Commit("sha1", "john_doe", "Commit 1", now, repo1),
                new Commit("sha2", "john_doe", "Commit 2", yesterday, repo1),
                new Commit("sha3", "john_doe", "Commit 3", now, repo2),
                new Commit("sha4", "other_user", "Commit 4", now, repo1) // Este no debe incluirse
        );

        when(commitRepository.findAll()).thenReturn(mockCommits);
        when(changeLeadTimeRepository.findAll()).thenReturn(Collections.emptyList());
        when(pullRequestRepository.findAll()).thenReturn(Collections.emptyList());
        when(commitParentRepository.findAll()).thenReturn(Collections.emptyList());

        // WHEN: Se solicitan las métricas sin filtros
        DeveloperMetricsResponse response = developerDashboardService.getDeveloperMetrics(githubUsername, null, null, null);

        // THEN: Se retornan las métricas correctas
        assertNotNull(response);
        assertEquals(githubUsername, response.developerUsername());
        assertEquals(3L, response.commitStats().totalCommits());
        assertEquals(2L, response.commitStats().repositoryCount());
        assertEquals(2, response.repositories().size());

        // Verificar que el repositorio con más commits esté primero
        assertEquals(2L, response.repositories().get(0).commitCount());
        assertEquals(1L, response.repositories().get(1).commitCount());

        // Verificar estadísticas de Pull Requests (sin PRs)
        assertNotNull(response.pullRequestStats());
        assertEquals(0L, response.pullRequestStats().totalPullRequests());
        assertEquals(0L, response.pullRequestStats().mergedPullRequests());
        assertEquals(0L, response.pullRequestStats().openPullRequests());

        // Verificar métricas DORA (sin deployments aún)
        assertNotNull(response.doraMetrics());
        assertNull(response.doraMetrics().averageLeadTimeHours());
        assertEquals(0L, response.doraMetrics().totalDeploymentCount());
        assertEquals(0L, response.doraMetrics().deployedCommitCount());
        assertNull(response.doraMetrics().changeFailureRate());
        assertEquals(0L, response.doraMetrics().failedDeploymentCount());
        assertTrue(response.doraMetrics().dailyMetrics().isEmpty());
    }

    @Test
    void testGetDeveloperMetrics_noCommits_returnsEmptyMetrics() {
        // GIVEN: Un developer sin commits
        String githubUsername = "new_developer";

        when(commitRepository.findAll()).thenReturn(Collections.emptyList());

        // WHEN: Se solicitan las métricas sin filtros
        DeveloperMetricsResponse response = developerDashboardService.getDeveloperMetrics(githubUsername, null, null, null);

        // THEN: Se retorna una respuesta vacía
        assertNotNull(response);
        assertEquals(githubUsername, response.developerUsername());
        assertEquals(0L, response.commitStats().totalCommits());
        assertEquals(0L, response.commitStats().repositoryCount());
        assertTrue(response.repositories().isEmpty());
        assertNull(response.commitStats().lastCommitDate());
        assertNull(response.commitStats().firstCommitDate());

        // Verificar métricas DORA vacías
        assertNotNull(response.doraMetrics());
        assertNull(response.doraMetrics().averageLeadTimeHours());
        assertEquals(0L, response.doraMetrics().totalDeploymentCount());
        assertEquals(0L, response.doraMetrics().deployedCommitCount());
        assertNull(response.doraMetrics().changeFailureRate());
        assertEquals(0L, response.doraMetrics().failedDeploymentCount());
        assertTrue(response.doraMetrics().dailyMetrics().isEmpty());
    }

    @Test
    void testGetDeveloperMetrics_caseInsensitiveUsername_returnsMetrics() {
        // GIVEN: Commits con username en diferentes casos
        String githubUsername = "John_Doe";

        RepositoryConfig repo = new RepositoryConfig("https://github.com/org/repo");
        LocalDateTime now = LocalDateTime.now();

        List<Commit> mockCommits = List.of(
                new Commit("sha1", "john_doe", "Commit 1", now, repo),
                new Commit("sha2", "JOHN_DOE", "Commit 2", now, repo),
                new Commit("sha3", "John_Doe", "Commit 3", now, repo)
        );

        when(commitRepository.findAll()).thenReturn(mockCommits);
        when(changeLeadTimeRepository.findAll()).thenReturn(Collections.emptyList());
        when(pullRequestRepository.findAll()).thenReturn(Collections.emptyList());
        when(commitParentRepository.findAll()).thenReturn(Collections.emptyList());

        // WHEN: Se solicitan las métricas sin filtros
        DeveloperMetricsResponse response = developerDashboardService.getDeveloperMetrics(githubUsername, null, null, null);

        // THEN: Se retornan todos los commits independientemente del case
        assertNotNull(response);
        assertEquals(3L, response.commitStats().totalCommits());
    }

    @Test
    void testGetDeveloperMetrics_withRepositoryFilter_filtersCorrectly() throws Exception {
        // GIVEN: Un developer con commits en dos repositorios con deployments
        String githubUsername = "john_doe";

        RepositoryConfig repo1 = new RepositoryConfig("https://github.com/org/repo1");
        RepositoryConfig repo2 = new RepositoryConfig("https://github.com/org/repo2");

        // Usar reflection para establecer los IDs (ya que no hay setter público)
        java.lang.reflect.Field idField = RepositoryConfig.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(repo1, 1L);
        idField.set(repo2, 2L);

        LocalDateTime now = LocalDateTime.now();

        Commit commit1 = new Commit("sha1", "john_doe", "Commit 1", now, repo1);
        Commit commit2 = new Commit("sha2", "john_doe", "Commit 2", now, repo1);
        Commit commit3 = new Commit("sha3", "john_doe", "Commit 3", now, repo2);

        List<Commit> mockCommits = List.of(commit1, commit2, commit3);

        // Crear deployments para simular que los commits fueron deployados
        Deployment deployment1 = new Deployment(null, repo1, "test-deployment", "sha1", "main", "production", "service1", "completed", "success", now, now);
        deployment1.setId(1L);
        Deployment deployment2 = new Deployment(null, repo1, "test-deployment", "sha2", "main", "production", "service1", "completed", "success", now, now);
        deployment2.setId(2L);
        Deployment deployment3 = new Deployment(null, repo2, "test-deployment", "sha3", "main", "production", "service2", "completed", "success", now, now);
        deployment3.setId(3L);

        ChangeLeadTime lt1 = new ChangeLeadTime(commit1, deployment1, 3600L);
        ChangeLeadTime lt2 = new ChangeLeadTime(commit2, deployment2, 3600L);
        ChangeLeadTime lt3 = new ChangeLeadTime(commit3, deployment3, 3600L);

        when(commitRepository.findAll()).thenReturn(mockCommits);
        when(changeLeadTimeRepository.findAll()).thenReturn(List.of(lt1, lt2, lt3));
        when(incidentRepository.findAll()).thenReturn(Collections.emptyList());
        when(pullRequestRepository.findAll()).thenReturn(Collections.emptyList());
        when(commitParentRepository.findAll()).thenReturn(Collections.emptyList());

        // WHEN: Se solicitan las métricas filtrando por repo1
        DeveloperMetricsResponse response = developerDashboardService.getDeveloperMetrics(
                githubUsername, null, null, List.of(1L));

        // THEN: Solo se retornan commits del repositorio filtrado
        assertNotNull(response);
        assertEquals(1, response.repositories().size());
        assertEquals(1L, response.repositories().get(0).repositoryId());
        assertEquals(2L, response.repositories().get(0).commitCount());
        // Verificar que commitStats también está filtrado
        assertEquals(2L, response.commitStats().totalCommits());
        assertEquals(1L, response.commitStats().repositoryCount());
    }

    @Test
    void testGetDeveloperMetrics_withDateFilter_filtersCorrectly() throws Exception {
        // GIVEN: Un developer con commits y deployments en diferentes fechas
        String githubUsername = "john_doe";

        RepositoryConfig repo = new RepositoryConfig("https://github.com/org/repo");

        // Usar reflection para establecer el ID
        java.lang.reflect.Field idField = RepositoryConfig.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(repo, 1L);

        LocalDateTime nov1 = LocalDateTime.of(2025, 11, 1, 10, 0);
        LocalDateTime nov2 = LocalDateTime.of(2025, 11, 2, 10, 0);
        LocalDateTime nov3 = LocalDateTime.of(2025, 11, 3, 10, 0);

        Commit commit1 = new Commit("sha1", "john_doe", "Commit 1", nov1, repo);
        Commit commit2 = new Commit("sha2", "john_doe", "Commit 2", nov2, repo);
        Commit commit3 = new Commit("sha3", "john_doe", "Commit 3", nov3, repo);

        List<Commit> mockCommits = List.of(commit1, commit2, commit3);

        Deployment deployment1 = new Deployment(null, repo, "test-deployment", "sha1", "main", "production", "service1", "completed", "success", nov1, nov1);
        deployment1.setId(1L);
        Deployment deployment2 = new Deployment(null, repo, "test-deployment", "sha2", "main", "production", "service1", "completed", "success", nov2, nov2);
        deployment2.setId(2L);
        Deployment deployment3 = new Deployment(null, repo, "test-deployment", "sha3", "main", "production", "service1", "completed", "success", nov3, nov3);
        deployment3.setId(3L);

        ChangeLeadTime lt1 = new ChangeLeadTime(commit1, deployment1, 3600L); // 1 hora
        ChangeLeadTime lt2 = new ChangeLeadTime(commit2, deployment2, 7200L); // 2 horas
        ChangeLeadTime lt3 = new ChangeLeadTime(commit3, deployment3, 10800L); // 3 horas

        when(commitRepository.findAll()).thenReturn(mockCommits);
        when(changeLeadTimeRepository.findAll()).thenReturn(List.of(lt1, lt2, lt3));
        when(incidentRepository.findAll()).thenReturn(Collections.emptyList());
        when(pullRequestRepository.findAll()).thenReturn(Collections.emptyList());
        when(commitParentRepository.findAll()).thenReturn(Collections.emptyList());

        // WHEN: Se solicitan las métricas filtrando por rango de fechas (solo Nov 2)
        LocalDate startDate = LocalDate.of(2025, 11, 2);
        LocalDate endDate = LocalDate.of(2025, 11, 2);
        DeveloperMetricsResponse response = developerDashboardService.getDeveloperMetrics(
                githubUsername, startDate, endDate, null);

        // THEN: Solo se incluyen las métricas del 2 de noviembre
        assertNotNull(response);
        assertEquals(1L, response.doraMetrics().totalDeploymentCount());
        assertEquals(1, response.doraMetrics().dailyMetrics().size());
        assertEquals(LocalDate.of(2025, 11, 2), response.doraMetrics().dailyMetrics().get(0).date());
        // Verificar que commitStats también está filtrado
        assertEquals(1L, response.commitStats().totalCommits());
        assertEquals(1L, response.commitStats().repositoryCount());
    }
}
