package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class GithubPullRequestDtoTest {

    @Test
    void testGettersAndSetters() {
        // Arrange
        GithubPullRequestDto dto = new GithubPullRequestDto();
        Long id = 1L;
        String state = "open";
        LocalDateTime createdAt = LocalDateTime.now().minusDays(1);
        LocalDateTime updatedAt = LocalDateTime.now().minusHours(12);
        LocalDateTime mergedAt = LocalDateTime.now();

        // Act
        dto.setId(id);
        dto.setState(state);
        dto.setCreatedAt(createdAt);
        dto.setUpdatedAt(updatedAt);
        dto.setMergedAt(mergedAt);

        // Assert
        assertEquals(id, dto.getId());
        assertEquals(state, dto.getState());
        assertEquals(createdAt, dto.getCreatedAt());
        assertEquals(updatedAt, dto.getUpdatedAt());
        assertEquals(mergedAt, dto.getMergedAt());
    }

    @Test
    void testEqualsHashCodeAndToStringContract() {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        GithubPullRequestDto dto1 = new GithubPullRequestDto();
        dto1.setId(1L);
        dto1.setState("open");
        dto1.setCreatedAt(now);
        dto1.setUpdatedAt(now);
        dto1.setMergedAt(now);

        GithubPullRequestDto dto2 = new GithubPullRequestDto();
        dto2.setId(1L);
        dto2.setState("open");
        dto2.setCreatedAt(now);
        dto2.setUpdatedAt(now);
        dto2.setMergedAt(now);

        GithubPullRequestDto dto3 = new GithubPullRequestDto();
        dto3.setId(2L);
        dto3.setState("closed");
        dto3.setCreatedAt(now.minusDays(1));
        dto3.setUpdatedAt(now.minusDays(1));
        dto3.setMergedAt(now.minusDays(1));

        // Assert for equals and hashCode
        // Test for identity
        assertTrue(dto1.equals(dto1), "Un objeto debe ser igual a sí mismo.");

        // Test for equality with another object
        assertTrue(dto1.equals(dto2), "Dos DTOs con los mismos datos deben ser iguales.");
        assertEquals(dto1.hashCode(), dto2.hashCode(), "Dos DTOs iguales deben tener el mismo hashCode.");

        // Test for inequality
        assertFalse(dto1.equals(dto3), "Dos DTOs con diferentes datos no deben ser iguales.");

        // Test against null and different class
        assertFalse(dto1.equals(null), "Un DTO no debe ser igual a null.");
        assertFalse(dto1.equals(new Object()), "Un DTO no debe ser igual a un objeto de otra clase.");

        // Assert for toString
        String dto1String = dto1.toString();
        assertTrue(dto1String.contains("id=1"), "toString debe contener el id.");
        assertTrue(dto1String.contains("state='open'"), "toString debe contener el estado.");
    }
}
