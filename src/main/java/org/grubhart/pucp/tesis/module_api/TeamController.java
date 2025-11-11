package org.grubhart.pucp.tesis.module_api;

import jakarta.validation.Valid;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_processor.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing teams
 */
@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamManagementService teamManagementService;

    public TeamController(TeamManagementService teamManagementService) {
        this.teamManagementService = teamManagementService;
    }

    /**
     * Create a new team
     */
    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        try {
            TeamResponse response = teamManagementService.createTeam(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all teams
     */
    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAllTeams() {
        List<TeamResponse> teams = teamManagementService.getAllTeams();
        return ResponseEntity.ok(teams);
    }

    /**
     * Get team by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<TeamDetailResponse> getTeam(@PathVariable Long id) {
        try {
            TeamDetailResponse response = teamManagementService.getTeam(id);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Update a team
     */
    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateTeamRequest request) {
        try {
            TeamResponse response = teamManagementService.updateTeam(id, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete a team
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        try {
            teamManagementService.deleteTeam(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get team members
     */
    @GetMapping("/{id}/members")
    public ResponseEntity<List<TeamMemberResponse>> getTeamMembers(@PathVariable Long id) {
        try {
            List<TeamMemberResponse> members = teamManagementService.getTeamMembers(id);
            return ResponseEntity.ok(members);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Assign a member to a team
     */
    @PostMapping("/{id}/members")
    public ResponseEntity<Void> assignMember(@PathVariable Long id,
                                            @Valid @RequestBody AssignMemberRequest request) {
        try {
            teamManagementService.assignMember(id, request.getUserId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * Remove a member from a team
     */
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        try {
            teamManagementService.removeMember(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get team repositories
     */
    @GetMapping("/{id}/repositories")
    public ResponseEntity<List<RepositoryConfig>> getTeamRepositories(@PathVariable Long id) {
        try {
            List<RepositoryConfig> repositories = teamManagementService.getTeamRepositories(id);
            return ResponseEntity.ok(repositories);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Assign a repository to a team
     */
    @PostMapping("/{id}/repositories")
    public ResponseEntity<Void> assignRepository(@PathVariable Long id,
                                                 @Valid @RequestBody AssignRepositoryRequest request) {
        try {
            teamManagementService.assignRepository(id, request.getRepositoryConfigId());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Remove a repository from a team
     */
    @DeleteMapping("/{id}/repositories/{repositoryId}")
    public ResponseEntity<Void> removeRepository(@PathVariable Long id, @PathVariable Long repositoryId) {
        try {
            teamManagementService.removeRepository(id, repositoryId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
