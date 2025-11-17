package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class EngineeringManagerDashboardServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

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
    private EngineeringManagerDashboardService engineeringManagerDashboardService;

    private User engineeringManager;
    private Team team1;
    private Team team2;
    private List<User> team1Members;
    private List<User> team2Members;
    private List<Commit> commits;
    private RepositoryConfig repository;

    @BeforeEach
    void setUp() {
        // Setup engineering manager
        engineeringManager = new User(1L, "em_user", "em@example.com");
        engineeringManager.setId(1L);

        // Setup teams
        team1 = new Team("Backend Team");
        team1.setId(100L);

        team2 = new Team("Frontend Team");
        team2.setId(200L);

        // Setup team 1 members
        User member1 = new User(2L, "developer1", "dev1@example.com");
        member1.setId(2L);
        member1.setTeamId(100L);

        User member2 = new User(3L, "developer2", "dev2@example.com");
        member2.setId(3L);
        member2.setTeamId(100L);

        team1Members = Arrays.asList(member1, member2);

        // Setup team 2 members
        User member3 = new User(4L, "developer3", "dev3@example.com");
        member3.setId(4L);
        member3.setTeamId(200L);

        team2Members = Collections.singletonList(member3);

        // Setup repository
        repository = new RepositoryConfig("https://github.com/test/repo");

        // Setup commits
        Commit commit1 = new Commit("sha1", "developer1", "Test commit 1", LocalDateTime.now().minusDays(5), repository);
        Commit commit2 = new Commit("sha2", "developer2", "Test commit 2", LocalDateTime.now().minusDays(3), repository);
        Commit commit3 = new Commit("sha3", "developer3", "Test commit 3", LocalDateTime.now().minusDays(2), repository);
        commits = Arrays.asList(commit1, commit2, commit3);
    }

    @Test
    void getEngineeringManagerMetrics_withValidEM_returnsMetrics() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("em_user"))
                .thenReturn(Optional.of(engineeringManager));
        when(teamRepository.findAll())
                .thenReturn(Arrays.asList(team1, team2));
        lenient().when(userRepository.findByTeamId(anyLong()))
                .thenAnswer(invocation -> {
                    Long teamId = invocation.getArgument(0);
                    if (teamId.equals(100L)) return team1Members;
                    if (teamId.equals(200L)) return team2Members;
                    return Collections.emptyList();
                });
        lenient().when(commitRepository.findAll())
                .thenReturn(commits);
        lenient().when(changeLeadTimeRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(pullRequestRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(commitParentRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(incidentRepository.findAll())
                .thenReturn(Collections.emptyList());

        // When
        EngineeringManagerMetricsResponse response = engineeringManagerDashboardService.getEngineeringManagerMetrics(
                "em_user", null, null, null, null, null);

        // Then
        assertNotNull(response);
        assertEquals("em_user", response.engineeringManagerUsername());
        assertEquals(2, response.totalTeams());
        assertEquals(3, response.totalDevelopers());
        assertEquals(2, response.teams().size());
        verify(userRepository).findByGithubUsernameIgnoreCase("em_user");
        verify(teamRepository).findAll();
    }

    @Test
    void getEngineeringManagerMetrics_engineeringManagerNotFound_throwsException() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("nonexistent"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                engineeringManagerDashboardService.getEngineeringManagerMetrics(
                        "nonexistent", null, null, null, null, null));
    }

    @Test
    void getEngineeringManagerMetrics_withTeamFilter_filtersCorrectly() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("em_user"))
                .thenReturn(Optional.of(engineeringManager));
        when(teamRepository.findAll())
                .thenReturn(Collections.singletonList(team1)); // Solo team1 porque filtramos por team1
        lenient().when(userRepository.findByTeamId(100L))
                .thenReturn(team1Members);
        lenient().when(commitRepository.findAll())
                .thenReturn(commits);
        lenient().when(changeLeadTimeRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(pullRequestRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(commitParentRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(incidentRepository.findAll())
                .thenReturn(Collections.emptyList());

        List<Long> teamFilter = Collections.singletonList(100L); // Solo Backend Team

        // When
        EngineeringManagerMetricsResponse response = engineeringManagerDashboardService.getEngineeringManagerMetrics(
                "em_user", null, null, null, teamFilter, null);

        // Then
        assertNotNull(response);
        assertEquals(1, response.totalTeams());
        assertEquals(2, response.totalDevelopers());
        assertEquals(1, response.teams().size());
        assertEquals("Backend Team", response.teams().get(0).teamName());
    }

    @Test
    void getEngineeringManagerMetrics_withMemberFilter_filtersCorrectly() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("em_user"))
                .thenReturn(Optional.of(engineeringManager));
        when(teamRepository.findAll())
                .thenReturn(Arrays.asList(team1, team2));
        lenient().when(userRepository.findByTeamId(100L))
                .thenReturn(team1Members);
        lenient().when(userRepository.findByTeamId(200L))
                .thenReturn(team2Members);
        lenient().when(commitRepository.findAll())
                .thenReturn(commits);
        lenient().when(changeLeadTimeRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(pullRequestRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(commitParentRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(incidentRepository.findAll())
                .thenReturn(Collections.emptyList());

        List<Long> memberFilter = Arrays.asList(2L, 3L); // Solo team1 members

        // When
        EngineeringManagerMetricsResponse response = engineeringManagerDashboardService.getEngineeringManagerMetrics(
                "em_user", null, null, null, null, memberFilter);

        // Then
        assertNotNull(response);
        assertEquals(2, response.totalDevelopers());
    }

    @Test
    void getEngineeringManagerMetrics_withInvalidMemberIds_throwsException() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("em_user"))
                .thenReturn(Optional.of(engineeringManager));
        when(teamRepository.findAll())
                .thenReturn(Collections.singletonList(team1)); // Solo team1
        when(userRepository.findByTeamId(100L))
                .thenReturn(team1Members); // Solo tiene members 2 y 3

        List<Long> invalidMemberFilter = Arrays.asList(2L, 999L); // 999L no pertenece a team1

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                engineeringManagerDashboardService.getEngineeringManagerMetrics(
                        "em_user", null, null, null, null, invalidMemberFilter));

        assertTrue(exception.getMessage().contains("no pertenecen a los equipos seleccionados"));
        assertTrue(exception.getMessage().contains("999"));
    }

    @Test
    void getEngineeringManagerMetrics_withTeamAndMemberFilter_validatesCorrectly() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("em_user"))
                .thenReturn(Optional.of(engineeringManager));
        when(teamRepository.findAll())
                .thenReturn(Arrays.asList(team1, team2));
        when(userRepository.findByTeamId(100L))
                .thenReturn(team1Members); // Members 2, 3

        List<Long> teamFilter = Collections.singletonList(100L); // Solo Backend Team
        List<Long> invalidMemberFilter = Collections.singletonList(4L); // Member 4 pertenece a team2, no team1

        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                engineeringManagerDashboardService.getEngineeringManagerMetrics(
                        "em_user", null, null, null, teamFilter, invalidMemberFilter));

        assertTrue(exception.getMessage().contains("no pertenecen a los equipos seleccionados"));
        assertTrue(exception.getMessage().contains("4"));
    }

    @Test
    void getEngineeringManagerMetrics_noTeams_returnsEmptyResponse() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("em_user"))
                .thenReturn(Optional.of(engineeringManager));
        when(teamRepository.findAll())
                .thenReturn(Collections.emptyList());

        // When
        EngineeringManagerMetricsResponse response = engineeringManagerDashboardService.getEngineeringManagerMetrics(
                "em_user", null, null, null, null, null);

        // Then
        assertNotNull(response);
        assertEquals(0, response.totalTeams());
        assertEquals(0, response.totalDevelopers());
        assertEquals(0, response.teams().size());
        assertEquals(0, response.aggregatedCommitStats().totalCommits());
    }

    @Test
    void getEngineeringManagerMetrics_teamsWithNoMembers_returnsEmptyResponse() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("em_user"))
                .thenReturn(Optional.of(engineeringManager));
        when(teamRepository.findAll())
                .thenReturn(Arrays.asList(team1, team2));
        when(userRepository.findByTeamId(100L))
                .thenReturn(Collections.emptyList());
        when(userRepository.findByTeamId(200L))
                .thenReturn(Collections.emptyList());

        // When
        EngineeringManagerMetricsResponse response = engineeringManagerDashboardService.getEngineeringManagerMetrics(
                "em_user", null, null, null, null, null);

        // Then
        assertNotNull(response);
        assertEquals(0, response.totalDevelopers());
        assertEquals(0, response.aggregatedCommitStats().totalCommits());
    }

    @Test
    void getEngineeringManagerMetrics_withMultipleTeamFilter_aggregatesCorrectly() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("em_user"))
                .thenReturn(Optional.of(engineeringManager));
        when(teamRepository.findAll())
                .thenReturn(Arrays.asList(team1, team2));
        lenient().when(userRepository.findByTeamId(100L))
                .thenReturn(team1Members);
        lenient().when(userRepository.findByTeamId(200L))
                .thenReturn(team2Members);
        lenient().when(commitRepository.findAll())
                .thenReturn(commits);
        lenient().when(changeLeadTimeRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(pullRequestRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(commitParentRepository.findAll())
                .thenReturn(Collections.emptyList());
        lenient().when(incidentRepository.findAll())
                .thenReturn(Collections.emptyList());

        List<Long> teamFilter = Arrays.asList(100L, 200L); // Ambos equipos

        // When
        EngineeringManagerMetricsResponse response = engineeringManagerDashboardService.getEngineeringManagerMetrics(
                "em_user", null, null, null, teamFilter, null);

        // Then
        assertNotNull(response);
        assertEquals(2, response.totalTeams());
        assertEquals(3, response.totalDevelopers());
        assertEquals(2, response.teams().size());
    }
}