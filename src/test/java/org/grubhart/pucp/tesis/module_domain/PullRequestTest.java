package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PullRequestTest {

    @Test
    void testGettersAndSetters() {
        // Arrange
        PullRequest pr = new PullRequest();
        Long id = 1L;
        RepositoryConfig repoConfig = new RepositoryConfig();
        String state = "open";
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime mergedAt = LocalDateTime.now();

        // Act
        pr.setId(id);
        pr.setRepository(repoConfig);
        pr.setState(state);
        pr.setCreatedAt(createdAt);
        pr.setMergedAt(mergedAt);

        // Assert
        assertEquals(id, pr.getId());
        assertEquals(repoConfig, pr.getRepository());
        assertEquals(state, pr.getState());
        assertEquals(createdAt, pr.getCreatedAt());
        assertEquals(mergedAt, pr.getMergedAt());
    }

    @Test
    void testEqualsAndHashCodeContract() {
        // Arrange
        PullRequest pr1 = new PullRequest();
        pr1.setId(123L);

        PullRequest pr2 = new PullRequest();
        pr2.setId(123L);

        PullRequest pr3 = new PullRequest();
        pr3.setId(456L);

        // Assert
        // Test for identity
        assertTrue(pr1.equals(pr1), "Un objeto debe ser igual a sí mismo.");

        // Test for equality
        assertTrue(pr1.equals(pr2), "Dos PullRequests con el mismo ID deben ser iguales.");
        assertTrue(pr2.equals(pr1), "La igualdad debe ser simétrica.");
        assertEquals(pr1.hashCode(), pr2.hashCode(), "Dos PullRequests iguales deben tener el mismo hashCode.");

        // Test for inequality
        assertFalse(pr1.equals(pr3), "Dos PullRequests con diferente ID no deben ser iguales.");
        assertFalse(pr1.equals(null), "Un PullRequest no debe ser igual a null.");
        assertFalse(pr1.equals(new Object()), "Un PullRequest no debe ser igual a un objeto de otra clase.");
    }

    @Test
    void testConstructorWithDto() {
        // Arrange
        GithubPullRequestDto dto = new GithubPullRequestDto();
        dto.setId(789L);
        dto.setState("closed");
        dto.setCreatedAt(LocalDateTime.now().minusHours(5));
        dto.setMergedAt(LocalDateTime.now().minusHours(1));

        RepositoryConfig repoConfig = new RepositoryConfig("https://github.com/test/repo");

        // Act
        PullRequest pr = new PullRequest(dto, repoConfig);

        // Assert
        assertEquals(dto.getId(), pr.getId());
        assertEquals(dto.getState(), pr.getState());
        assertEquals(dto.getCreatedAt(), pr.getCreatedAt());
        assertEquals(dto.getMergedAt(), pr.getMergedAt());
        assertEquals(repoConfig, pr.getRepository());
    }
}
