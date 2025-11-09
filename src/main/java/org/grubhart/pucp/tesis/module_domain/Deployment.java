package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "deployment")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long githubId;

    private String name;
    private String sha;
    private String headBranch;
    private String environment;

    /**
     * The Datadog service name this deployment is associated with.
     * Used to correlate deployments with incidents for DORA metrics.
     */
    private String serviceName;

    private String status;
    private String conclusion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private boolean leadTimeProcessed = false;

    public Deployment() {
        // JPA constructor
    }

    public Deployment(Long githubId, String name, String sha, String headBranch, String environment, String status, String conclusion, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.githubId = githubId;
        this.name = name;
        this.sha = sha;
        this.headBranch = headBranch;
        this.environment = environment;
        this.status = status;
        this.conclusion = conclusion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Deployment(Long githubId, String name, String sha, String headBranch, String environment, String serviceName, String status, String conclusion, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.githubId = githubId;
        this.name = name;
        this.sha = sha;
        this.headBranch = headBranch;
        this.environment = environment;
        this.serviceName = serviceName;
        this.status = status;
        this.conclusion = conclusion;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGithubId() {
        return githubId;
    }

    public void setGithubId(Long githubId) {
        this.githubId = githubId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public String getHeadBranch() {
        return headBranch;
    }

    public void setHeadBranch(String headBranch) {
        this.headBranch = headBranch;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
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

    public boolean isLeadTimeProcessed() {
        return leadTimeProcessed;
    }

    public void setLeadTimeProcessed(boolean leadTimeProcessed) {
        this.leadTimeProcessed = leadTimeProcessed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Deployment that = (Deployment) o;
        return Objects.equals(id, that.id) && Objects.equals(githubId, that.githubId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, githubId);
    }
}