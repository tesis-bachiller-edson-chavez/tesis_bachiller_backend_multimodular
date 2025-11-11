package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a team in the organization.
 * A team can have multiple tech leads and multiple developers.
 * A tech lead can only belong to one team at a time.
 * A developer can only belong to one team at a time.
 */
@Entity
@Table(name = "teams")
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /**
     * The repositories this team works on.
     * A team can work on multiple repositories, and a repository can have multiple teams.
     */
    @ManyToMany
    @JoinTable(
            name = "team_repositories",
            joinColumns = @JoinColumn(name = "team_id"),
            inverseJoinColumns = @JoinColumn(name = "repository_config_id")
    )
    private Set<RepositoryConfig> repositories = new HashSet<>();

    public Team() {
    }

    public Team(String name) {
        this.name = name;
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

    public Set<RepositoryConfig> getRepositories() {
        return repositories;
    }

    public void setRepositories(Set<RepositoryConfig> repositories) {
        this.repositories = repositories;
    }

    // Helper methods
    public void addRepository(RepositoryConfig repository) {
        this.repositories.add(repository);
    }

    public void removeRepository(RepositoryConfig repository) {
        this.repositories.remove(repository);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Team team = (Team) o;
        return Objects.equals(id, team.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Team{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", repositoriesCount=" + repositories.size() +
                '}';
    }
}
