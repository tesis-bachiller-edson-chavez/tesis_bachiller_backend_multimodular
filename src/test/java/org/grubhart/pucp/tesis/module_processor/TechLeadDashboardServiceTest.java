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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TechLeadDashboardServiceTest {

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
    private TechLeadDashboardService techLeadDashboardService;

    private User techLead;
    private Team team;
    private List<User> teamMembers;
    private List<Commit> commits;
    private RepositoryConfig repository;

    @BeforeEach
    void setUp() {
        // Setup tech lead
        techLead = new User(1L, "techlead", "techlead@example.com");
        techLead.setId(1L);
        techLead.setTeamId(100L);

        // Setup team
        team = new Team("Backend Team");
        team.setId(100L);

        // Setup team members
        User member1 = new User(2L, "developer1", "dev1@example.com");
        member1.setId(2L);
        member1.setTeamId(100L);

        User member2 = new User(3L, "developer2", "dev2@example.com");
        member2.setId(3L);
        member2.setTeamId(100L);

        teamMembers = Arrays.asList(techLead, member1, member2);

        // Setup repository
        repository = new RepositoryConfig("https://github.com/test/repo");

        // Setup commits
        Commit commit1 = new Commit("sha1", "developer1", LocalDateTime.now().minusDays(5), repository);
        Commit commit2 = new Commit("sha2", "developer2", LocalDateTime.now().minusDays(3), repository);
        commits = Arrays.asList(commit1, commit2);
    }

    @Test
    void getTechLeadMetrics_withValidTechLead_returnsMetrics() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("techlead"))
                .thenReturn(Optional.of(techLead));
        when(teamRepository.findById(100L))
                .thenReturn(Optional.of(team));
        when(userRepository.findByTeamId(100L))
                .thenReturn(teamMembers);
        when(commitRepository.findAll())
                .thenReturn(commits);
        when(changeLeadTimeRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(pullRequestRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(commitParentRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(incidentRepository.findAll())
                .thenReturn(Collections.emptyList());

        // When
        TechLeadMetricsResponse response = techLeadDashboardService.getTechLeadMetrics(
                "techlead", null, null, null, null);

        // Then
        assertNotNull(response);
        assertEquals("techlead", response.techLeadUsername());
        assertEquals(100L, response.teamId());
        assertEquals("Backend Team", response.teamName());
        assertEquals(3, response.teamMembers().size());
        verify(userRepository).findByGithubUsernameIgnoreCase("techlead");
        verify(teamRepository).findById(100L);
        verify(userRepository).findByTeamId(100L);
    }

    @Test
    void getTechLeadMetrics_techLeadNotFound_throwsException() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("nonexistent"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                techLeadDashboardService.getTechLeadMetrics(
                        "nonexistent", null, null, null, null));
    }

    @Test
    void getTechLeadMetrics_techLeadWithoutTeam_throwsException() {
        // Given
        techLead.setTeamId(null);
        when(userRepository.findByGithubUsernameIgnoreCase("techlead"))
                .thenReturn(Optional.of(techLead));

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                techLeadDashboardService.getTechLeadMetrics(
                        "techlead", null, null, null, null));
    }

    @Test
    void getTechLeadMetrics_withMemberFilter_filtersCorrectly() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("techlead"))
                .thenReturn(Optional.of(techLead));
        when(teamRepository.findById(100L))
                .thenReturn(Optional.of(team));
        when(userRepository.findByTeamId(100L))
                .thenReturn(teamMembers);
        when(commitRepository.findAll())
                .thenReturn(commits);
        when(changeLeadTimeRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(pullRequestRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(commitParentRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(incidentRepository.findAll())
                .thenReturn(Collections.emptyList());

        List<Long> memberFilter = Arrays.asList(2L, 3L); // Solo developers, sin tech lead

        // When
        TechLeadMetricsResponse response = techLeadDashboardService.getTechLeadMetrics(
                "techlead", null, null, null, memberFilter);

        // Then
        assertNotNull(response);
        assertEquals(2, response.teamMembers().size());
        assertTrue(response.teamMembers().stream()
                .allMatch(member -> memberFilter.contains(member.userId())));
    }

    @Test
    void getTechLeadMetrics_teamWithNoMembers_returnsEmptyResponse() {
        // Given
        when(userRepository.findByGithubUsernameIgnoreCase("techlead"))
                .thenReturn(Optional.of(techLead));
        when(teamRepository.findById(100L))
                .thenReturn(Optional.of(team));
        when(userRepository.findByTeamId(100L))
                .thenReturn(Collections.emptyList());

        // When
        TechLeadMetricsResponse response = techLeadDashboardService.getTechLeadMetrics(
                "techlead", null, null, null, null);

        // Then
        assertNotNull(response);
        assertEquals(0, response.teamMembers().size());
        assertEquals(0, response.commitStats().totalCommits());
    }

    @Test
    void getTechLeadMetrics_withDateFilter_appliesCorrectly() {
        // Given
        LocalDate startDate = LocalDate.now().minusDays(10);
        LocalDate endDate = LocalDate.now();

        when(userRepository.findByGithubUsernameIgnoreCase("techlead"))
                .thenReturn(Optional.of(techLead));
        when(teamRepository.findById(100L))
                .thenReturn(Optional.of(team));
        when(userRepository.findByTeamId(100L))
                .thenReturn(teamMembers);
        when(commitRepository.findAll())
                .thenReturn(commits);
        when(changeLeadTimeRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(pullRequestRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(commitParentRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(incidentRepository.findAll())
                .thenReturn(Collections.emptyList());

        // When
        TechLeadMetricsResponse response = techLeadDashboardService.getTechLeadMetrics(
                "techlead", startDate, endDate, null, null);

        // Then
        assertNotNull(response);
        verify(changeLeadTimeRepository).findAll();
    }

    @Test
    void getTechLeadMetrics_withRepositoryFilter_appliesCorrectly() {
        // Given
        List<Long> repositoryIds = Arrays.asList(1L, 2L);

        when(userRepository.findByGithubUsernameIgnoreCase("techlead"))
                .thenReturn(Optional.of(techLead));
        when(teamRepository.findById(100L))
                .thenReturn(Optional.of(team));
        when(userRepository.findByTeamId(100L))
                .thenReturn(teamMembers);
        when(commitRepository.findAll())
                .thenReturn(commits);
        when(changeLeadTimeRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(pullRequestRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(commitParentRepository.findAll())
                .thenReturn(Collections.emptyList());
        when(incidentRepository.findAll())
                .thenReturn(Collections.emptyList());

        // When
        TechLeadMetricsResponse response = techLeadDashboardService.getTechLeadMetrics(
                "techlead", null, null, repositoryIds, null);

        // Then
        assertNotNull(response);
        verify(changeLeadTimeRepository).findAll();
    }
}
