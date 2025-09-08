package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class SyncStatus {

    @Id
    private String jobName;

    private LocalDateTime lastSuccessfulRun;

    protected SyncStatus() {
    }

    public SyncStatus(String jobName, LocalDateTime lastSuccessfulRun) {
        this.jobName = jobName;
        this.lastSuccessfulRun = lastSuccessfulRun;
    }

    public String getJobName() {
        return jobName;
    }

    public LocalDateTime getLastSuccessfulRun() {
        return lastSuccessfulRun;
    }
}
