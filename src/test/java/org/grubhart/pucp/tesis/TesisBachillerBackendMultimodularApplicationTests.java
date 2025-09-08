package org.grubhart.pucp.tesis;

import org.grubhart.pucp.tesis.module_domain.GithubCommitCollector;
import org.grubhart.pucp.tesis.module_domain.GithubUserAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class TesisBachillerBackendMultimodularApplicationTests {

    @MockitoBean
    private GithubUserAuthenticator githubUserAuthenticator;

    @MockitoBean
    private GithubCommitCollector githubCommitCollector;

    @Test
    void contextLoads() {
    }

    @Test
    void main() {
        // Simplemente invoca al método main para la cobertura.
        TesisBachillerBackendMultimodularApplication.main(new String[]{});
    }
}
