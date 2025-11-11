package org.grubhart.pucp.tesis.module_processor;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for assigning a repository to a team
 */
public class AssignRepositoryRequest {

    @NotNull(message = "Repository Config ID is required")
    private Long repositoryConfigId;

    public AssignRepositoryRequest() {
    }

    public AssignRepositoryRequest(Long repositoryConfigId) {
        this.repositoryConfigId = repositoryConfigId;
    }

    public Long getRepositoryConfigId() {
        return repositoryConfigId;
    }

    public void setRepositoryConfigId(Long repositoryConfigId) {
        this.repositoryConfigId = repositoryConfigId;
    }
}
