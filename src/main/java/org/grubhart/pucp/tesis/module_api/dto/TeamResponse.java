package org.grubhart.pucp.tesis.module_api.dto;

import org.grubhart.pucp.tesis.module_domain.Team;

import java.util.List;

/**
 * Response DTO for a team (summary view)
 */
public class TeamResponse {

    private Long id;
    private String name;
    private int memberCount;
    private int techLeadCount;
    private int repositoryCount;
    private List<Long> techLeadIds;

    public TeamResponse() {
    }

    public TeamResponse(Long id, String name, int memberCount, int techLeadCount, int repositoryCount, List<Long> techLeadIds) {
        this.id = id;
        this.name = name;
        this.memberCount = memberCount;
        this.techLeadCount = techLeadCount;
        this.repositoryCount = repositoryCount;
        this.techLeadIds = techLeadIds;
    }

    public static TeamResponse fromTeam(Team team, int memberCount, int techLeadCount, List<Long> techLeadIds) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                memberCount,
                techLeadCount,
                team.getRepositories().size(),
                techLeadIds
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public int getTechLeadCount() {
        return techLeadCount;
    }

    public void setTechLeadCount(int techLeadCount) {
        this.techLeadCount = techLeadCount;
    }

    public int getRepositoryCount() {
        return repositoryCount;
    }

    public void setRepositoryCount(int repositoryCount) {
        this.repositoryCount = repositoryCount;
    }

    public List<Long> getTechLeadIds() {
        return techLeadIds;
    }

    public void setTechLeadIds(List<Long> techLeadIds) {
        this.techLeadIds = techLeadIds;
    }
}
