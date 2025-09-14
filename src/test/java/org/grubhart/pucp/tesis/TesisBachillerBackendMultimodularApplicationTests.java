package org.grubhart.pucp.tesis;

import org.grubhart.pucp.tesis.module_collector.github.GithubClientImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class TesisBachillerBackendMultimodularApplicationTests {

    @MockitoBean
    private GithubClientImpl githubClient; // Mockeamos la clase concreta

    @Test
    void contextLoads() {
    }

    @Test
    void main() {
        // Simplemente invoca al método main para la cobertura.
        TesisBachillerBackendMultimodularApplication.main(new String[]{});
    }
}
