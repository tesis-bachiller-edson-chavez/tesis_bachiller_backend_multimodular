package org.grubhart.pucp.tesis.module_api;

import org.grubhart.pucp.tesis.module_api.dto.*;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_processor.TeamManagementService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TeamController - API for team management")
class TeamControllerTest {

    @Mock
    private TeamManagementService teamManagementService;

    @InjectMocks
    private TeamController teamController;

    private TeamResponse teamResponse;
    private TeamDetailResponse teamDetailResponse;
    private CreateTeamRequest createTeamRequest;
    private UpdateTeamRequest updateTeamRequest;

    @BeforeEach
    void setUp() {
        teamResponse = new TeamResponse(1L, "Test Team", 5, 2, 3, List.of(10L, 20L));
        teamDetailResponse = new TeamDetailResponse();
        teamDetailResponse.setId(1L);
        teamDetailResponse.setName("Test Team");

        createTeamRequest = new CreateTeamRequest("New Team", null);
        updateTeamRequest = new UpdateTeamRequest("Updated Team", null);
    }

    @Test
    @DisplayName("POST /teams - Should create team successfully")
    void createTeam_withValidRequest_returnsCreated() {
        // Given
        when(teamManagementService.createTeam(any(CreateTeamRequest.class))).thenReturn(teamResponse);

        // When
        ResponseEntity<TeamResponse> response = teamController.createTeam(createTeamRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo("Test Team");
        verify(teamManagementService).createTeam(any(CreateTeamRequest.class));
    }

    @Test
    @DisplayName("POST /teams - Should return bad request on duplicate name")
    void createTeam_withDuplicateName_returnsBadRequest() {
        // Given
        when(teamManagementService.createTeam(any(CreateTeamRequest.class)))
                .thenThrow(new IllegalArgumentException("Team already exists"));

        // When
        ResponseEntity<TeamResponse> response = teamController.createTeam(createTeamRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /teams - Should return all teams")
    void getAllTeams_returnsAllTeams() {
        // Given
        List<TeamResponse> teams = List.of(teamResponse);
        when(teamManagementService.getAllTeams()).thenReturn(teams);

        // When
        ResponseEntity<List<TeamResponse>> response = teamController.getAllTeams();

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(teamManagementService).getAllTeams();
    }

    @Test
    @DisplayName("GET /teams/{id} - Should return team details")
    void getTeam_withValidId_returnsTeamDetails() {
        // Given
        when(teamManagementService.getTeam(1L)).thenReturn(teamDetailResponse);

        // When
        ResponseEntity<TeamDetailResponse> response = teamController.getTeam(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(1L);
        verify(teamManagementService).getTeam(1L);
    }

    @Test
    @DisplayName("GET /teams/{id} - Should return not found for invalid id")
    void getTeam_withInvalidId_returnsNotFound() {
        // Given
        when(teamManagementService.getTeam(999L))
                .thenThrow(new IllegalArgumentException("Team not found"));

        // When
        ResponseEntity<TeamDetailResponse> response = teamController.getTeam(999L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("PUT /teams/{id} - Should update team successfully")
    void updateTeam_withValidRequest_returnsUpdatedTeam() {
        // Given
        when(teamManagementService.updateTeam(anyLong(), any(UpdateTeamRequest.class)))
                .thenReturn(teamResponse);

        // When
        ResponseEntity<TeamResponse> response = teamController.updateTeam(1L, updateTeamRequest);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        verify(teamManagementService).updateTeam(anyLong(), any(UpdateTeamRequest.class));
    }

    @Test
    @DisplayName("DELETE /teams/{id} - Should delete team successfully")
    void deleteTeam_withValidId_returnsNoContent() {
        // Given
        doNothing().when(teamManagementService).deleteTeam(1L);

        // When
        ResponseEntity<Void> response = teamController.deleteTeam(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(teamManagementService).deleteTeam(1L);
    }

    @Test
    @DisplayName("DELETE /teams/{id} - Should return bad request when team has members")
    void deleteTeam_withMembers_returnsBadRequest() {
        // Given
        doThrow(new IllegalStateException("Team has members"))
                .when(teamManagementService).deleteTeam(1L);

        // When
        ResponseEntity<Void> response = teamController.deleteTeam(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /teams/{id}/members - Should return team members")
    void getTeamMembers_returnsMembers() {
        // Given
        List<TeamMemberResponse> members = List.of(
                new TeamMemberResponse(10L, "dev1", "dev1@example.com", "Dev One", null, false)
        );
        when(teamManagementService.getTeamMembers(1L)).thenReturn(members);

        // When
        ResponseEntity<List<TeamMemberResponse>> response = teamController.getTeamMembers(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(teamManagementService).getTeamMembers(1L);
    }

    @Test
    @DisplayName("POST /teams/{id}/members - Should assign member successfully")
    void assignMember_withValidRequest_returnsOk() {
        // Given
        AssignMemberRequest request = new AssignMemberRequest(10L);
        doNothing().when(teamManagementService).assignMember(1L, 10L);

        // When
        ResponseEntity<Void> response = teamController.assignMember(1L, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(teamManagementService).assignMember(1L, 10L);
    }

    @Test
    @DisplayName("POST /teams/{id}/members - Should return conflict when user already in team")
    void assignMember_userAlreadyInTeam_returnsConflict() {
        // Given
        AssignMemberRequest request = new AssignMemberRequest(10L);
        doThrow(new IllegalStateException("User already in team"))
                .when(teamManagementService).assignMember(1L, 10L);

        // When
        ResponseEntity<Void> response = teamController.assignMember(1L, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("DELETE /teams/{id}/members/{userId} - Should remove member successfully")
    void removeMember_withValidIds_returnsNoContent() {
        // Given
        doNothing().when(teamManagementService).removeMember(1L, 10L);

        // When
        ResponseEntity<Void> response = teamController.removeMember(1L, 10L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(teamManagementService).removeMember(1L, 10L);
    }

    @Test
    @DisplayName("GET /teams/{id}/repositories - Should return team repositories")
    void getTeamRepositories_returnsRepositories() {
        // Given
        List<RepositoryConfig> repositories = List.of(
                new RepositoryConfig("https://github.com/test/repo")
        );
        when(teamManagementService.getTeamRepositories(1L)).thenReturn(repositories);

        // When
        ResponseEntity<List<RepositoryConfig>> response = teamController.getTeamRepositories(1L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(teamManagementService).getTeamRepositories(1L);
    }

    @Test
    @DisplayName("POST /teams/{id}/repositories - Should assign repository successfully")
    void assignRepository_withValidRequest_returnsOk() {
        // Given
        AssignRepositoryRequest request = new AssignRepositoryRequest(5L);
        doNothing().when(teamManagementService).assignRepository(1L, 5L);

        // When
        ResponseEntity<Void> response = teamController.assignRepository(1L, request);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(teamManagementService).assignRepository(1L, 5L);
    }

    @Test
    @DisplayName("DELETE /teams/{id}/repositories/{repoId} - Should remove repository successfully")
    void removeRepository_withValidIds_returnsNoContent() {
        // Given
        doNothing().when(teamManagementService).removeRepository(1L, 5L);

        // When
        ResponseEntity<Void> response = teamController.removeRepository(1L, 5L);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(teamManagementService).removeRepository(1L, 5L);
    }
}
