package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamManagementServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RepositoryConfigRepository repositoryConfigRepository;

    @InjectMocks
    private TeamManagementService teamManagementService;

    private Team testTeam;
    private User testDeveloper;
    private User testTechLead;
    private RepositoryConfig testRepository;
    private Role developerRole;
    private Role techLeadRole;

    @BeforeEach
    void setUp() {
        // Create roles
        developerRole = new Role(RoleName.DEVELOPER);
        developerRole.setId(1L);

        techLeadRole = new Role(RoleName.TECH_LEAD);
        techLeadRole.setId(2L);

        // Create test team
        testTeam = new Team("Test Team");
        testTeam.setId(1L);

        // Create test developer
        testDeveloper = new User(12345L, "testdev", "test@example.com");
        testDeveloper.setId(10L);
        testDeveloper.setRoles(Set.of(developerRole));

        // Create test tech lead
        testTechLead = new User(67890L, "techlead", "lead@example.com");
        testTechLead.setId(20L);
        testTechLead.setRoles(Set.of(techLeadRole));

        // Create test repository
        testRepository = new RepositoryConfig("https://github.com/test/repo");
        testRepository.setDatadogServiceName("test-service");
    }

    @Test
    void testCreateTeam_withValidName_createsTeam() {
        // GIVEN
        CreateTeamRequest request = new CreateTeamRequest("New Team", null);
        when(teamRepository.existsByName("New Team")).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(userRepository.countByTeamId(1L)).thenReturn(0L);
        when(userRepository.countByTeamIdAndRoles_Name(1L, RoleName.TECH_LEAD)).thenReturn(0L);
        when(userRepository.findByTeamIdAndRoles_Name(1L, RoleName.TECH_LEAD)).thenReturn(Collections.emptyList());

        // WHEN
        TeamResponse response = teamManagementService.createTeam(request);

        // THEN
        assertNotNull(response);
        assertEquals("New Team", response.getName());
        verify(teamRepository).save(any(Team.class));
    }

    @Test
    void testCreateTeam_withDuplicateName_throwsException() {
        // GIVEN
        CreateTeamRequest request = new CreateTeamRequest("Existing Team", null);
        when(teamRepository.existsByName("Existing Team")).thenReturn(true);

        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            teamManagementService.createTeam(request);
        });
        verify(teamRepository, never()).save(any(Team.class));
    }

    @Test
    void testCreateTeam_withTechLeads_assignsTechLeads() {
        // GIVEN
        CreateTeamRequest request = new CreateTeamRequest("New Team", List.of(20L));
        when(teamRepository.existsByName("New Team")).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(userRepository.findById(20L)).thenReturn(Optional.of(testTechLead));
        when(userRepository.countByTeamId(1L)).thenReturn(1L);
        when(userRepository.countByTeamIdAndRoles_Name(1L, RoleName.TECH_LEAD)).thenReturn(1L);
        when(userRepository.findByTeamIdAndRoles_Name(1L, RoleName.TECH_LEAD)).thenReturn(List.of(testTechLead));

        // WHEN
        TeamResponse response = teamManagementService.createTeam(request);

        // THEN
        assertNotNull(response);
        assertEquals(1, response.getTechLeadCount());
        verify(userRepository).save(testTechLead);
        assertEquals(1L, testTechLead.getTeamId());
    }

    @Test
    void testUpdateTeam_withNewName_updatesName() {
        // GIVEN
        UpdateTeamRequest request = new UpdateTeamRequest("Updated Team", null);
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(teamRepository.existsByName("Updated Team")).thenReturn(false);
        when(teamRepository.save(any(Team.class))).thenReturn(testTeam);
        when(userRepository.findByTeamIdAndRoles_Name(1L, RoleName.TECH_LEAD)).thenReturn(Collections.emptyList());
        when(userRepository.countByTeamId(1L)).thenReturn(0L);
        when(userRepository.countByTeamIdAndRoles_Name(1L, RoleName.TECH_LEAD)).thenReturn(0L);

        // WHEN
        TeamResponse response = teamManagementService.updateTeam(1L, request);

        // THEN
        assertEquals("Updated Team", testTeam.getName());
        verify(teamRepository).save(testTeam);
    }

    @Test
    void testDeleteTeam_withNoMembers_deletesTeam() {
        // GIVEN
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(userRepository.countByTeamId(1L)).thenReturn(0L);

        // WHEN
        teamManagementService.deleteTeam(1L);

        // THEN
        verify(teamRepository).delete(testTeam);
    }

    @Test
    void testDeleteTeam_withMembers_throwsException() {
        // GIVEN
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(userRepository.countByTeamId(1L)).thenReturn(2L);

        // WHEN & THEN
        assertThrows(IllegalStateException.class, () -> {
            teamManagementService.deleteTeam(1L);
        });
        verify(teamRepository, never()).delete(any(Team.class));
    }

    @Test
    void testAssignMember_developer_assignsSuccessfully() {
        // GIVEN
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(userRepository.findById(10L)).thenReturn(Optional.of(testDeveloper));

        // WHEN
        teamManagementService.assignMember(1L, 10L);

        // THEN
        assertEquals(1L, testDeveloper.getTeamId());
        verify(userRepository).save(testDeveloper);
    }

    @Test
    void testAssignMember_developerAlreadyInAnotherTeam_throwsException() {
        // GIVEN
        testDeveloper.setTeamId(2L); // Already in team 2
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(userRepository.findById(10L)).thenReturn(Optional.of(testDeveloper));

        // WHEN & THEN
        assertThrows(IllegalStateException.class, () -> {
            teamManagementService.assignMember(1L, 10L);
        });
    }

    @Test
    void testAssignMember_techLead_assignsSuccessfully() {
        // GIVEN
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(userRepository.findById(20L)).thenReturn(Optional.of(testTechLead));

        // WHEN
        teamManagementService.assignMember(1L, 20L);

        // THEN
        assertEquals(1L, testTechLead.getTeamId());
        verify(userRepository).save(testTechLead);
    }

    @Test
    void testAssignMember_techLeadAlreadyInAnotherTeam_throwsException() {
        // GIVEN
        testTechLead.setTeamId(2L); // Already in team 2
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(userRepository.findById(20L)).thenReturn(Optional.of(testTechLead));

        // WHEN & THEN
        assertThrows(IllegalStateException.class, () -> {
            teamManagementService.assignMember(1L, 20L);
        });
    }

    @Test
    void testRemoveMember_validMember_removesSuccessfully() {
        // GIVEN
        testDeveloper.setTeamId(1L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(testDeveloper));

        // WHEN
        teamManagementService.removeMember(1L, 10L);

        // THEN
        assertNull(testDeveloper.getTeamId());
        verify(userRepository).save(testDeveloper);
    }

    @Test
    void testRemoveMember_userNotInTeam_throwsException() {
        // GIVEN
        testDeveloper.setTeamId(2L); // In different team
        when(userRepository.findById(10L)).thenReturn(Optional.of(testDeveloper));

        // WHEN & THEN
        assertThrows(IllegalArgumentException.class, () -> {
            teamManagementService.removeMember(1L, 10L);
        });
    }

    @Test
    void testAssignRepository_validRepository_assignsSuccessfully() {
        // GIVEN
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(repositoryConfigRepository.findById(5L)).thenReturn(Optional.of(testRepository));
        when(teamRepository.save(any(Team.class))).thenReturn(testTeam);

        // WHEN
        teamManagementService.assignRepository(1L, 5L);

        // THEN
        assertTrue(testTeam.getRepositories().contains(testRepository));
        verify(teamRepository).save(testTeam);
    }

    @Test
    void testRemoveRepository_validRepository_removesSuccessfully() {
        // GIVEN
        testTeam.addRepository(testRepository);
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(repositoryConfigRepository.findById(5L)).thenReturn(Optional.of(testRepository));
        when(teamRepository.save(any(Team.class))).thenReturn(testTeam);

        // WHEN
        teamManagementService.removeRepository(1L, 5L);

        // THEN
        assertFalse(testTeam.getRepositories().contains(testRepository));
        verify(teamRepository).save(testTeam);
    }

    @Test
    void testGetTeamMembers_returnsAllMembers() {
        // GIVEN
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));
        when(userRepository.findByTeamId(1L)).thenReturn(List.of(testDeveloper, testTechLead));

        // WHEN
        List<TeamMemberResponse> members = teamManagementService.getTeamMembers(1L);

        // THEN
        assertEquals(2, members.size());
    }

    @Test
    void testGetAllTeams_returnsAllTeams() {
        // GIVEN
        Team team2 = new Team("Team 2");
        team2.setId(2L);
        when(teamRepository.findAll()).thenReturn(List.of(testTeam, team2));
        when(userRepository.countByTeamId(anyLong())).thenReturn(0L);
        when(userRepository.countByTeamIdAndRoles_Name(anyLong(), any(RoleName.class))).thenReturn(0L);
        when(userRepository.findByTeamIdAndRoles_Name(anyLong(), any(RoleName.class))).thenReturn(Collections.emptyList());

        // WHEN
        List<TeamResponse> teams = teamManagementService.getAllTeams();

        // THEN
        assertEquals(2, teams.size());
    }
}
