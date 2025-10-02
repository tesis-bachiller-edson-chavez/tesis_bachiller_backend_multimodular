package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.*;

@Entity
@Table(name = "commit_parent", uniqueConstraints = @UniqueConstraint(columnNames = {"commit_sha", "parent_sha"}))
public class CommitParent {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "commit_sha")
    private Commit commit;

    @ManyToOne
    @JoinColumn(name = "parent_sha")
    private Commit parent;

    public CommitParent() {
    }

    public CommitParent(Commit commit, Commit parent) {
        this.commit = commit;
        this.parent = parent;
    }

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

    public Commit getParent() {
        return parent;
    }

    public void setParent(Commit parent) {
        this.parent = parent;
    }
}
