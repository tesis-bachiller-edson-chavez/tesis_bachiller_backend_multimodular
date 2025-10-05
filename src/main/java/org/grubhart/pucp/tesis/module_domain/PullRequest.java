package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "PULL_REQUESTS")
public class PullRequest {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryConfig repository;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime mergedAt;

    @Column(name = "first_commit_sha")
    private String firstCommitSha;

    public PullRequest() {
    }

    /**
     * Constructor para crear una entidad PullRequest a partir de un DTO.
     * @param dto El DTO con los datos del Pull Request de GitHub.
     * @param repository La entidad RepositoryConfig a la que pertenece este PR.
     */
    public PullRequest(GithubPullRequestDto dto, RepositoryConfig repository) {
        this.id = dto.getId();
        this.repository = repository;
        this.state = dto.getState();
        this.createdAt = dto.getCreatedAt();
        this.mergedAt = dto.getMergedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RepositoryConfig getRepository() {
        return repository;
    }

    public void setRepository(RepositoryConfig repository) {
        this.repository = repository;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getMergedAt() {
        return mergedAt;
    }

    public void setMergedAt(LocalDateTime mergedAt) {
        this.mergedAt = mergedAt;
    }

    public String getFirstCommitSha() {
        return firstCommitSha;
    }

    public void setFirstCommitSha(String firstCommitSha) {
        this.firstCommitSha = firstCommitSha;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PullRequest that = (PullRequest) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
