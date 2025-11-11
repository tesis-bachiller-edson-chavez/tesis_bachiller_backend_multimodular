package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_api.dto.*;
import org.grubhart.pucp.tesis.module_domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing teams and their relationships with members and repositories
 */
@Service
public class TeamManagementService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final RepositoryConfigRepository repositoryConfigRepository;

    public TeamManagementService(TeamRepository teamRepository,
                                 UserRepository userRepository,
                                 RepositoryConfigRepository repositoryConfigRepository) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.repositoryConfigRepository = repositoryConfigRepository;
    }

    /**
     * Create a new team
     */
    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request) {
        // Validate team name is unique
        if (teamRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Team with name '" + request.getName() + "' already exists");
        }

        Team team = new Team(request.getName());
        team = teamRepository.save(team);

        // Assign tech leads if provided
        if (request.getTechLeadIds() != null && !request.getTechLeadIds().isEmpty()) {
            for (Long techLeadId : request.getTechLeadIds()) {
                assignTechLead(team.getId(), techLeadId);
            }
        }

        return buildTeamResponse(team);
    }

    /**
     * Update an existing team
     */
    @Transactional
    public TeamResponse updateTeam(Long teamId, UpdateTeamRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found with id: " + teamId));

        // Validate team name is unique (if changed)
        if (!team.getName().equals(request.getName()) && teamRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Team with name '" + request.getName() + "' already exists");
        }

        team.setName(request.getName());
        team = teamRepository.save(team);

        // Update tech leads if provided
        if (request.getTechLeadIds() != null) {
            // Remove current tech leads
            List<User> currentTechLeads = userRepository.findByTeamIdAndRoles_Name(teamId, RoleName.TECH_LEAD);
            for (User techLead : currentTechLeads) {
                techLead.setTeamId(null);
                userRepository.save(techLead);
            }

            // Assign new tech leads
            for (Long techLeadId : request.getTechLeadIds()) {
                assignTechLead(teamId, techLeadId);
            }
        }

        return buildTeamResponse(team);
    }

    /**
     * Delete a team
     */
    @Transactional
    public void deleteTeam(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found with id: " + teamId));

        // Validate team has no members
        long memberCount = userRepository.countByTeamId(teamId);
        if (memberCount > 0) {
            throw new IllegalStateException("Cannot delete team with active members. Please remove all members first.");
        }

        teamRepository.delete(team);
    }

    /**
     * Get team by ID
     */
    @Transactional(readOnly = true)
    public TeamDetailResponse getTeam(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found with id: " + teamId));

        List<User> members = userRepository.findByTeamId(teamId);
        List<User> techLeads = userRepository.findByTeamIdAndRoles_Name(teamId, RoleName.TECH_LEAD);

        return TeamDetailResponse.fromTeam(team, members, techLeads);
    }

    /**
     * Get all teams
     */
    @Transactional(readOnly = true)
    public List<TeamResponse> getAllTeams() {
        return teamRepository.findAll().stream()
                .map(this::buildTeamResponse)
                .collect(Collectors.toList());
    }

    /**
     * Assign a member to a team
     */
    @Transactional
    public void assignMember(Long teamId, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found with id: " + teamId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        // Check if user is a tech lead
        boolean isTechLead = user.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.TECH_LEAD);

        if (isTechLead) {
            assignTechLead(teamId, userId);
        } else {
            // Validate user doesn't already belong to another team
            if (user.getTeamId() != null && !user.getTeamId().equals(teamId)) {
                throw new IllegalStateException("User already belongs to another team. Please remove from current team first.");
            }

            user.setTeamId(teamId);
            userRepository.save(user);
        }
    }

    /**
     * Remove a member from a team
     */
    @Transactional
    public void removeMember(Long teamId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        if (user.getTeamId() == null || !user.getTeamId().equals(teamId)) {
            throw new IllegalArgumentException("User is not a member of this team");
        }

        user.setTeamId(null);
        userRepository.save(user);
    }

    /**
     * Assign a repository to a team
     */
    @Transactional
    public void assignRepository(Long teamId, Long repositoryConfigId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found with id: " + teamId));

        RepositoryConfig repository = repositoryConfigRepository.findById(repositoryConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + repositoryConfigId));

        team.addRepository(repository);
        teamRepository.save(team);
    }

    /**
     * Remove a repository from a team
     */
    @Transactional
    public void removeRepository(Long teamId, Long repositoryConfigId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found with id: " + teamId));

        RepositoryConfig repository = repositoryConfigRepository.findById(repositoryConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with id: " + repositoryConfigId));

        team.removeRepository(repository);
        teamRepository.save(team);
    }

    /**
     * Get all members of a team
     */
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeamMembers(Long teamId) {
        // Validate team exists
        teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found with id: " + teamId));

        List<User> members = userRepository.findByTeamId(teamId);
        return members.stream()
                .map(TeamMemberResponse::fromUser)
                .collect(Collectors.toList());
    }

    /**
     * Get all repositories of a team
     */
    @Transactional(readOnly = true)
    public List<RepositoryConfig> getTeamRepositories(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found with id: " + teamId));

        return team.getRepositories().stream().collect(Collectors.toList());
    }

    // Helper methods

    private void assignTechLead(Long teamId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        // Validate user has TECH_LEAD role
        boolean hasTechLeadRole = user.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.TECH_LEAD);

        if (!hasTechLeadRole) {
            throw new IllegalArgumentException("User must have TECH_LEAD role to be assigned as tech lead");
        }

        // Validate tech lead doesn't already belong to another team
        if (user.getTeamId() != null && !user.getTeamId().equals(teamId)) {
            throw new IllegalStateException("Tech lead already belongs to another team. A tech lead can only belong to one team at a time.");
        }

        user.setTeamId(teamId);
        userRepository.save(user);
    }

    private TeamResponse buildTeamResponse(Team team) {
        long memberCount = userRepository.countByTeamId(team.getId());
        long techLeadCount = userRepository.countByTeamIdAndRoles_Name(team.getId(), RoleName.TECH_LEAD);
        List<Long> techLeadIds = userRepository.findByTeamIdAndRoles_Name(team.getId(), RoleName.TECH_LEAD)
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());

        return TeamResponse.fromTeam(team, (int) memberCount, (int) techLeadCount, techLeadIds);
    }
}
