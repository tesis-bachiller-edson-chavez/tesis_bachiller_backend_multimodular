package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryConfigTest {

    @Test
    @DisplayName("Debe parsear correctamente una URL de GitHub válida")
    void shouldParseValidGitHubUrl() {
        // Arrange
        RepositoryConfig config = new RepositoryConfig("https://github.com/test-owner/test-repo");

        // Act & Assert
        assertEquals("test-owner", config.getOwner());
        assertEquals("test-repo", config.getRepoName());
    }

    @Test
    @DisplayName("Debe parsear URL con barra final, cubriendo la rama 'parts[length - 1].isEmpty()' a verdadero")
    void shouldParseUrlWithTrailingSlash_byHandlingEmptyLastPart() {
        // Arrange
        // Una URL que termina en '/' causa que split() cree una cadena vacía al final del array.
        // Esta prueba verifica que la lógica `if (parts[length - 1].isEmpty())` se ejecuta correctamente.
        RepositoryConfig config = new RepositoryConfig("https://github.com/test-owner/test-repo/");

        // Act & Assert
        assertEquals("test-owner", config.getOwner());
        assertEquals("test-repo", config.getRepoName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Debe devolver null para URLs en blanco, cubriendo la condición 'repositoryUrl.isBlank()'")
    void shouldReturnNullForBlankUrls(String blankUrl) {
        // Arrange
        RepositoryConfig config = new RepositoryConfig(blankUrl);

        // Act & Assert
        assertNull(config.getOwner());
        assertNull(config.getRepoName());
    }

    @Test
    @DisplayName("Debe devolver null para una URL nula, cubriendo la condición 'repositoryUrl == null'")
    void shouldReturnNullForNullUrl() {
        // Arrange
        RepositoryConfig config = new RepositoryConfig(null);

        // Act & Assert
        assertNull(config.getOwner());
        assertNull(config.getRepoName());
    }

    @Test
    @DisplayName("Debe devolver null si la URL no tiene suficientes partes para el owner")
    void shouldReturnNullWhenUrlHasNotEnoughPartsForOwner() {
        // Arrange
        // Esta URL, después del split, no tendrá un índice [length - 2] válido.
        RepositoryConfig config = new RepositoryConfig("https://github.com");

        // Act & Assert
        assertNull(config.getOwner(), "Owner debe ser null si no hay suficientes partes.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "//", "///"})
    @DisplayName("Debe devolver null para URLs que son solo delimitadores, cubriendo 'length > 0' como falso")
    void shouldReturnNullForDelimiterOnlyUrls(String delimiterUrl) {
        // Arrange
        RepositoryConfig config = new RepositoryConfig(delimiterUrl);

        // Act & Assert
        assertNull(config.getOwner());
        assertNull(config.getRepoName());
    }

    @Test
    @DisplayName("Debe devolver null si la parte del owner o repo está en blanco, cubriendo 'isBlank()'")
    void shouldReturnNullForBlankUrlParts() {
        // Arrange
        RepositoryConfig configWithBlankOwner = new RepositoryConfig("https://github.com//test-repo");
        RepositoryConfig configWithBlankRepo = new RepositoryConfig("https://github.com/test-owner/   ");

        // Act & Assert
        assertNull(configWithBlankOwner.getOwner(), "Owner debe ser null si la parte de la URL está en blanco.");
        assertEquals("test-repo", configWithBlankOwner.getRepoName());

        assertEquals("test-owner", configWithBlankRepo.getOwner());
        assertNull(configWithBlankRepo.getRepoName(), "RepoName debe ser null si la parte de la URL es solo espacios.");
    }

    @Test
    @DisplayName("Debe almacenar y devolver correctamente la URL y el ID inicial")
    void shouldHoldAndReturnRepositoryUrlAndInitialId() {
        // 1. Arrange
        String url = "https://github.com/test/repo";

        // 2. Act
        RepositoryConfig config = new RepositoryConfig(url);

        // 3. Assert
        // Verifica que la URL se puede recuperar correctamente.
        assertEquals(url, config.getRepositoryUrl());

        // Verifica que el ID es nulo antes de que la entidad sea persistida por JPA.
        assertNull(config.getId());
    }

    @Test
    @DisplayName("Debe devolver null si la URL no tiene un componente de ruta (path es nulo)")
    void shouldReturnNullWhenUrlHasNoPath() {
        // 1. Arrange
        // Una URI como 'mailto' no tiene un componente de ruta, lo que hará que
        // uri.getPath() devuelva null. Esto prueba la condición 'path == null'.
        RepositoryConfig config = new RepositoryConfig("mailto:test@example.com");

        // 2. Act & 3. Assert
        assertNull(config.getOwner(), "Owner debe ser null cuando la URI no tiene ruta.");
        assertNull(config.getRepoName(), "RepoName debe ser null cuando la URI no tiene ruta.");
    }


    @Test
    @DisplayName("Debe devolver null si la ruta de la URL contiene solo barras, cubriendo 'trimmedPath.isEmpty()'")
    void shouldReturnNullWhenPathContainsOnlySlashes() {
        // 1. Arrange
        // Esta URL tiene un path "//", que no es vacío ni "/", por lo que pasa el primer 'if'.
        // Después del 'trimming', se convierte en una cadena vacía, cubriendo el segundo 'if'.
        RepositoryConfig config = new RepositoryConfig("https://github.com//");

        // 2. Act & 3. Assert
        assertNull(config.getOwner(), "Owner debe ser null si la ruta solo contiene barras.");
        assertNull(config.getRepoName(), "RepoName debe ser null si la ruta solo contiene barras.");
    }

    @Test
    @DisplayName("Debe devolver null si la parte del repo es una barra doble, cubriendo '!parts[1].isBlank()' como falso")
    void shouldReturnNullWhenRepoPartIsEffectivelyBlank() {
        // Arrange
        // Esta URL, al hacer split, genera un elemento vacío entre "owner" y "final"
        // La lógica actual de getRepoName tomará ese elemento vacío como `parts[1]`.
        RepositoryConfig config = new RepositoryConfig("https://github.com/owner//final");

        // Act
        String repoName = config.getRepoName();

        // Assert
        // La lógica actual es `parts[1]`, que es "". isBlank() es true, !isBlank() es false.
        // El if no se cumple y devuelve null.
        assertNull(repoName);
    }

    @Test
    @DisplayName("Debe establecer y obtener todos los valores correctamente")
    void shouldSetAndGetValuesCorrectly() {
        // Arrange
        RepositoryConfig config = new RepositoryConfig();
        String url = "https://github.com/new-owner/new-repo";
        String serviceName = "new-service";
        String workflowFile = "new-deploy.yml";

        // Act
        config.setRepositoryUrl(url);
        config.setDatadogServiceName(serviceName);
        config.setDeploymentWorkflowFileName(workflowFile);

        // Assert
        assertNull(config.getId()); // ID es nulo antes de persistir
        assertEquals(url, config.getRepositoryUrl());
        assertEquals(serviceName, config.getDatadogServiceName());
        assertEquals(workflowFile, config.getDeploymentWorkflowFileName());
        assertEquals("new-owner", config.getOwner());
        assertEquals("new-repo", config.getRepoName());
    }

    // Nota de Cobertura: El caso `length < 1` en getOwner() y getRepoName() es ahora cubierto
    // por la prueba `shouldReturnNullForDelimiterOnlyUrls`, que produce un array de longitud 0.
}
