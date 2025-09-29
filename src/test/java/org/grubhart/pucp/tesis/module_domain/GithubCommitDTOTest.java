package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GithubCommitDtoTest {

    @Test
    @DisplayName("Debe establecer y obtener correctamente todos los campos del DTO y sus clases anidadas")
    void testSettersAndGetters() {
        // 1. Arrange - Crear datos de prueba
        String sha = "test-sha-123";
        String message = "feat: new feature";
        String authorName = "Test Author";
        String authorEmail = "author@example.com";
        Date commitDate = new Date();
        String authorLogin = "test-author-login";

        // Crear instancias de las clases anidadas
        GithubCommitDto.CommitAuthor commitAuthor = new GithubCommitDto.CommitAuthor();
        commitAuthor.setName(authorName);
        commitAuthor.setEmail(authorEmail);
        commitAuthor.setDate(commitDate);

        GithubCommitDto.Commit commit = new GithubCommitDto.Commit();
        commit.setMessage(message);
        commit.setAuthor(commitAuthor);

        GithubCommitDto.Author author = new GithubCommitDto.Author();
        author.setLogin(authorLogin);

        // Crear la instancia principal del DTO
        GithubCommitDto dto = new GithubCommitDto();

        // 2. Act - Usar los setters para poblar el objeto
        dto.setSha(sha);
        dto.setCommit(commit);
        dto.setAuthor(author);

        // 3. Assert - Usar los getters y verificar que los valores son correctos
        assertEquals(sha, dto.getSha());
        assertNotNull(dto.getCommit());
        assertNotNull(dto.getAuthor());

        // Verificar el objeto Commit anidado
        assertEquals(message, dto.getCommit().getMessage());
        assertNotNull(dto.getCommit().getAuthor());

        // Verificar el objeto CommitAuthor anidado
        assertEquals(authorName, dto.getCommit().getAuthor().getName());
        assertEquals(authorEmail, dto.getCommit().getAuthor().getEmail());
        assertEquals(commitDate, dto.getCommit().getAuthor().getDate());

        // Verificar el objeto Author anidado
        assertEquals(authorLogin, dto.getAuthor().getLogin());
    }
}