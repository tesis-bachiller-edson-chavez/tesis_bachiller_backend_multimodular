package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_domain.Team;
import org.grubhart.pucp.tesis.module_domain.User;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Response DTO for team details (full view with members and repositories)
 */
public class TeamDetailResponse {

    private Long id;
    private String name;
    private List<TeamMemberResponse> members;
    private List<TeamMemberResponse> techLeads;
    private List<RepositoryInfo> repositories;

    public TeamDetailResponse() {
    }

    public TeamDetailResponse(Long id, String name, List<TeamMemberResponse> members,
                             List<TeamMemberResponse> techLeads, List<RepositoryInfo> repositories) {
        this.id = id;
        this.name = name;
        this.members = members;
        this.techLeads = techLeads;
        this.repositories = repositories;
    }

    public static TeamDetailResponse fromTeam(Team team, List<User> members, List<User> techLeads) {
        List<TeamMemberResponse> memberResponses = members.stream()
                .map(TeamMemberResponse::fromUser)
                .collect(Collectors.toList());

        List<TeamMemberResponse> techLeadResponses = techLeads.stream()
                .map(TeamMemberResponse::fromUser)
                .collect(Collectors.toList());

        List<RepositoryInfo> repositoryInfos = team.getRepositories().stream()
                .map(repo -> new RepositoryInfo(
                        repo.getId(),
                        repo.getRepositoryUrl(),
                        repo.getDatadogServiceName(),
                        repo.getOwner(),
                        repo.getRepoName()
                ))
                .collect(Collectors.toList());

        return new TeamDetailResponse(
                team.getId(),
                team.getName(),
                memberResponses,
                techLeadResponses,
                repositoryInfos
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

    public List<TeamMemberResponse> getMembers() {
        return members;
    }

    public void setMembers(List<TeamMemberResponse> members) {
        this.members = members;
    }

    public List<TeamMemberResponse> getTechLeads() {
        return techLeads;
    }

    public void setTechLeads(List<TeamMemberResponse> techLeads) {
        this.techLeads = techLeads;
    }

    public List<RepositoryInfo> getRepositories() {
        return repositories;
    }

    public void setRepositories(List<RepositoryInfo> repositories) {
        this.repositories = repositories;
    }

    public static class RepositoryInfo {
        private Long id;
        private String repositoryUrl;
        private String datadogServiceName;
        private String owner;
        private String repoName;

        public RepositoryInfo() {
        }

        public RepositoryInfo(Long id, String repositoryUrl, String datadogServiceName,
                            String owner, String repoName) {
            this.id = id;
            this.repositoryUrl = repositoryUrl;
            this.datadogServiceName = datadogServiceName;
            this.owner = owner;
            this.repoName = repoName;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getRepositoryUrl() {
            return repositoryUrl;
        }

        public void setRepositoryUrl(String repositoryUrl) {
            this.repositoryUrl = repositoryUrl;
        }

        public String getDatadogServiceName() {
            return datadogServiceName;
        }

        public void setDatadogServiceName(String datadogServiceName) {
            this.datadogServiceName = datadogServiceName;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner) {
            this.owner = owner;
        }

        public String getRepoName() {
            return repoName;
        }

        public void setRepoName(String repoName) {
            this.repoName = repoName;
        }
    }
}
