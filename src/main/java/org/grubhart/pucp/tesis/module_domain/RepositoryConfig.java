package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class RepositoryConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String repositoryUrl;

    protected RepositoryConfig() {
    }

    public RepositoryConfig(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public Long getId() {
        return id;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getOwner() {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return null;
        }
        String[] parts = repositoryUrl.split("/");
        if (parts.length < 2) {
            return null;
        }
        String owner = parts[parts.length - 2];
        return owner.isBlank() ? null : owner;
    }

    public String getRepoName() {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return null;
        }
        String[] parts = repositoryUrl.split("/");
        if (parts.length < 1) {
            return null;
        }
        String repoName = parts[parts.length - 1];
        return repoName.isBlank() ? null : repoName;
    }
}
