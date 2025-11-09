package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GithubRepositoryDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Debe ignorar propiedades desconocidas durante la deserialización del DTO principal")
    void shouldIgnoreUnknownPropertiesWhenDeserializing() {
        // Arrange - JSON con propiedades adicionales no mapeadas
        String jsonWithUnknownProperty = """
            {
              "id": 1,
              "name": "repo-test",
              "full_name": "owner/repo-test",
              "html_url": "https://github.com/owner/repo-test",
              "private": false,
              "owner": { "login": "owner" },
              "unknown_field_1": "some value",
              "extra_property": 123,
              "another_field": true
            }
            """;

        // Act & Assert
        assertDoesNotThrow(() -> {
            GithubRepositoryDto dto = objectMapper.readValue(jsonWithUnknownProperty, GithubRepositoryDto.class);

            // Verificar que TODOS los campos conocidos se mapearon correctamente
            assertEquals(1L, dto.id(), "El campo id debe mapearse correctamente");
            assertEquals("repo-test", dto.name(), "El campo name debe mapearse correctamente");
            assertEquals("owner/repo-test", dto.fullName(), "El campo fullName debe mapearse correctamente");
            assertEquals("https://github.com/owner/repo-test", dto.htmlUrl(), "El campo htmlUrl debe mapearse correctamente");
            assertEquals(false, dto.isPrivate(), "El campo isPrivate debe mapearse correctamente");
            assertEquals("owner", dto.owner().login(), "El campo owner.login debe mapearse correctamente");
        }, "La deserialización no debe fallar por propiedades desconocidas.");
    }

    @Test
    @DisplayName("Debe ignorar propiedades desconocidas en el record nested Owner")
    void shouldIgnoreUnknownPropertiesInOwnerRecord() {
        // Arrange - JSON con propiedades adicionales en el objeto owner
        String jsonWithUnknownOwnerProperties = """
            {
              "id": 2,
              "name": "test-repo",
              "full_name": "testuser/test-repo",
              "html_url": "https://github.com/testuser/test-repo",
              "private": true,
              "owner": {
                "login": "testuser",
                "unknown_owner_field": "should be ignored",
                "extra_owner_prop": 456
              }
            }
            """;

        // Act & Assert
        assertDoesNotThrow(() -> {
            GithubRepositoryDto dto = objectMapper.readValue(jsonWithUnknownOwnerProperties, GithubRepositoryDto.class);

            // Verificar que el owner se deserializó correctamente ignorando propiedades desconocidas
            assertEquals("testuser", dto.owner().login(), "El campo owner.login debe mapearse correctamente");
            assertEquals(2L, dto.id(), "El campo id debe mapearse correctamente");
            assertEquals(true, dto.isPrivate(), "El campo isPrivate debe mapearse correctamente");
        }, "La deserialización no debe fallar por propiedades desconocidas en el objeto owner.");
    }
}
