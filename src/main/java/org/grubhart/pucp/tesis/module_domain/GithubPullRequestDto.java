package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * DTO para deserializar la respuesta de la API de GitHub para un Pull Request.
 */
public class GithubPullRequestDto {

    private Long id;
    private int number;
    private String state;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @JsonProperty("merged_at")
    private LocalDateTime mergedAt;

    private String firstCommitSha;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
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
        GithubPullRequestDto that = (GithubPullRequestDto) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GithubPullRequestDto{" +
                "id=" + id +
                ", number=" + number +
                ", state='" + state + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", mergedAt=" + mergedAt +
                ", firstCommitSha='" + firstCommitSha + '\'' +
                '}';
    }
}
