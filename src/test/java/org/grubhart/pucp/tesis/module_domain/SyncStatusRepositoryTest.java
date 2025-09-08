package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SyncStatusRepositoryTest {

    @Autowired
    private SyncStatusRepository repository;

    @Test
    void shouldSaveAndRetrieveSyncStatus() {
        LocalDateTime now = LocalDateTime.now();
        SyncStatus status = new SyncStatus("COMMIT_SYNC", now);
        SyncStatus savedStatus = repository.save(status);

        assertThat(savedStatus).isNotNull();
        assertThat(savedStatus.getJobName()).isEqualTo("COMMIT_SYNC");
        assertThat(savedStatus.getLastSuccessfulRun()).isEqualTo(now);

        SyncStatus retrievedStatus = repository.findById("COMMIT_SYNC").orElse(null);
        assertThat(retrievedStatus).isNotNull();
        assertThat(retrievedStatus.getLastSuccessfulRun()).isEqualTo(now);
    }
}
