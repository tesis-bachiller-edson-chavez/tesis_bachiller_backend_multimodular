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
    private String headBranch;
    private String status;
    private String conclusion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Constructor sin argumentos (requerido por JPA)
    public Deployment() {
    }

    // Constructor con todos los argumentos (para reemplazar al Builder)
    public Deployment(Long githubId, String name, String headBranch, String status, String conclusion, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.githubId = githubId;
        this.name = name;
        this.headBranch = headBranch;
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

    public String getHeadBranch() {
        return headBranch;
    }

    public void setHeadBranch(String headBranch) {
        this.headBranch = headBranch;
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
