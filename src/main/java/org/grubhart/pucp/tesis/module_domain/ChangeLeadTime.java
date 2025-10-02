package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "change_lead_time")
public class ChangeLeadTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "commit_sha", referencedColumnName = "sha")
    private Commit commit;

    @ManyToOne
    @JoinColumn(name = "deployment_id", referencedColumnName = "id")
    private Deployment deployment;

    private long leadTimeInSeconds;

    public ChangeLeadTime() {
        // JPA constructor
    }

    public ChangeLeadTime(Commit commit, Deployment deployment, long leadTimeInSeconds) {
        this.commit = commit;
        this.deployment = deployment;
        this.leadTimeInSeconds = leadTimeInSeconds;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Commit getCommit() {
        return commit;
    }

    public void setCommit(Commit commit) {
        this.commit = commit;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public void setDeployment(Deployment deployment) {
        this.deployment = deployment;
    }

    public long getLeadTimeInSeconds() {
        return leadTimeInSeconds;
    }

    public void setLeadTimeInSeconds(long leadTimeInSeconds) {
        this.leadTimeInSeconds = leadTimeInSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChangeLeadTime that = (ChangeLeadTime) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
