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
}
