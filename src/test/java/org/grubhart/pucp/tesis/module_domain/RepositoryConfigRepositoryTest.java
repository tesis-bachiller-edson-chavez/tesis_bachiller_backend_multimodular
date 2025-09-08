package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RepositoryConfigRepositoryTest {

    @Autowired
    private RepositoryConfigRepository repository;

    @Test
    void shouldSaveAndRetrieveRepositoryConfig() {
        RepositoryConfig config = new RepositoryConfig("owner/repo-name");
        RepositoryConfig savedConfig = repository.save(config);

        assertThat(savedConfig).isNotNull();
        assertThat(savedConfig.getId()).isNotNull();
        assertThat(savedConfig.getRepositoryUrl()).isEqualTo("owner/repo-name");

        RepositoryConfig retrievedConfig = repository.findById(savedConfig.getId()).orElse(null);
        assertThat(retrievedConfig).isNotNull();
        assertThat(retrievedConfig.getRepositoryUrl()).isEqualTo("owner/repo-name");
    }
}
