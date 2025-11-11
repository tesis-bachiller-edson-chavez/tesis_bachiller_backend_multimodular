package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.ChangeLeadTimeRepository;
import org.grubhart.pucp.tesis.module_domain.Commit;
import org.grubhart.pucp.tesis.module_domain.CommitRepository;
import org.grubhart.pucp.tesis.module_domain.IncidentRepository;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        // WHEN: Se solicitan las métricas
        DeveloperMetricsResponse response = developerDashboardService.getDeveloperMetrics(githubUsername);

        // THEN: Se retornan las métricas correctas
        assertNotNull(response);
        assertEquals(githubUsername, response.developerUsername());
        assertEquals(3L, response.commitStats().totalCommits());
        assertEquals(2L, response.commitStats().repositoryCount());
        assertEquals(2, response.repositories().size());

        // Verificar que el repositorio con más commits esté primero
        assertEquals(2L, response.repositories().get(0).commitCount());
        assertEquals(1L, response.repositories().get(1).commitCount());

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

        // WHEN: Se solicitan las métricas
        DeveloperMetricsResponse response = developerDashboardService.getDeveloperMetrics(githubUsername);

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

        // WHEN: Se solicitan las métricas
        DeveloperMetricsResponse response = developerDashboardService.getDeveloperMetrics(githubUsername);

        // THEN: Se retornan todos los commits independientemente del case
        assertNotNull(response);
        assertEquals(3L, response.commitStats().totalCommits());
    }
}
