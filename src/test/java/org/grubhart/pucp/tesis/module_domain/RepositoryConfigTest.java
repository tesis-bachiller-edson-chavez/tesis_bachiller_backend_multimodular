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

    // Nota de Cobertura: El caso `length < 1` en getOwner() y getRepoName() es ahora cubierto
    // por la prueba `shouldReturnNullForDelimiterOnlyUrls`, que produce un array de longitud 0.
}
