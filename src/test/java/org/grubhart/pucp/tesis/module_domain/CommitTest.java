package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test para la entidad Commit.
 * Este test verifica que los constructores, getters y setters de la entidad
 * funcionan correctamente, asegurando la cobertura de código para el boilerplate
 * requerido por JPA/Hibernate.
 */
class CommitTest {

    @Test
    @DisplayName("Debe crear una instancia de Commit usando el constructor con todos los argumentos")
    void shouldCreateCommitWithAllArgsConstructor() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        String sha = "12345abcde";
        String author = "test-author";
        String message = "feat: test commit";

        // When
        Commit commit = new Commit(sha, author, message, now);

        // Then
        assertEquals(sha, commit.getSha());
        assertEquals(author, commit.getAuthor());
        assertEquals(message, commit.getMessage());
        assertEquals(now, commit.getDate());
    }

    @Test
    @DisplayName("Debe poder usar el constructor por defecto y los setters")
    void shouldCreateCommitWithDefaultConstructorAndSetters() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        String sha = "fedcba9876";
        String author = "another-author";
        String message = "fix: test setters";

        // When: Se invoca el constructor por defecto y luego los setters
        Commit commit = new Commit(); // Se invoca el constructor por defecto
        commit.setSha(sha);
        commit.setAuthor(author);
        commit.setMessage(message);
        commit.setDate(now);

        // Then
        assertNotNull(commit, "El constructor por defecto no debe devolver null.");
        assertEquals(sha, commit.getSha());
        assertEquals(author, commit.getAuthor());
        assertEquals(message, commit.getMessage());
        assertEquals(now, commit.getDate());
    }
}