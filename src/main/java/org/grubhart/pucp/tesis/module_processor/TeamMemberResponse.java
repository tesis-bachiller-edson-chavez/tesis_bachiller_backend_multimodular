package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.RoleName;
import org.grubhart.pucp.tesis.module_domain.User;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response DTO for a team member
 */
public class TeamMemberResponse {

    private Long userId;
    private String githubUsername;
    private String email;
    private String name;
    private Set<String> roles;
    private boolean isTechLead;

    public TeamMemberResponse() {
    }

    public TeamMemberResponse(Long userId, String githubUsername, String email, String name, Set<String> roles, boolean isTechLead) {
        this.userId = userId;
        this.githubUsername = githubUsername;
        this.email = email;
        this.name = name;
        this.roles = roles;
        this.isTechLead = isTechLead;
    }

    public static TeamMemberResponse fromUser(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());

        boolean isTechLead = user.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.TECH_LEAD);

        return new TeamMemberResponse(
                user.getId(),
                user.getGithubUsername(),
                user.getEmail(),
                user.getName(),
                roleNames,
                isTechLead
        );
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getGithubUsername() {
        return githubUsername;
    }

    public void setGithubUsername(String githubUsername) {
        this.githubUsername = githubUsername;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public boolean isTechLead() {
        return isTechLead;
    }

    public void setTechLead(boolean techLead) {
        isTechLead = techLead;
    }
}
