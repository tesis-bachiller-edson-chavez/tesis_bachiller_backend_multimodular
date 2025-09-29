package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubWorkflowRunsResponseTest {

    @Test
    void shouldSetAndGetTotalCount() {
        // Arrange
        GitHubWorkflowRunsResponse response = new GitHubWorkflowRunsResponse();
        int expectedTotalCount = 42;

        // Act
        response.setTotalCount(expectedTotalCount);
        int actualTotalCount = response.getTotalCount();

        // Assert
        assertEquals(expectedTotalCount, actualTotalCount, "El total count obtenido debe ser igual al establecido.");
    }

    @Test
    void shouldSetAndGetWorkflowRuns() {
        // Arrange
        GitHubWorkflowRunsResponse response = new GitHubWorkflowRunsResponse();
        List<GitHubWorkflowRunDto> expectedWorkflowRuns = new ArrayList<>();
        expectedWorkflowRuns.add(new GitHubWorkflowRunDto()); // Añadimos una instancia para un caso más completo

        // Act
        response.setWorkflowRuns(expectedWorkflowRuns);
        List<GitHubWorkflowRunDto> actualWorkflowRuns = response.getWorkflowRuns();

        // Assert
        assertEquals(expectedWorkflowRuns, actualWorkflowRuns, "La lista de workflow runs obtenida debe ser igual a la establecida.");
    }
}
