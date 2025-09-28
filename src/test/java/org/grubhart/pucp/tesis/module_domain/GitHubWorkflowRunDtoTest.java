package org.grubhart.pucp.tesis.module_domain;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GitHubWorkflowRunDtoTest {

    @Test
    void testGettersAndSetters() {
        // GIVEN
        GitHubWorkflowRunDto dto = new GitHubWorkflowRunDto();
        Long id = 1L;
        String name = "Test Workflow";
        String headBranch = "feature-branch";
        String status = "completed";
        String conclusion = "success";
        LocalDateTime now = LocalDateTime.now();

        // WHEN
        dto.setId(id);
        dto.setName(name);
        dto.setHeadBranch(headBranch);
        dto.setStatus(status);
        dto.setConclusion(conclusion);
        dto.setCreatedAt(now);
        dto.setUpdatedAt(now);

        // THEN
        assertEquals(id, dto.getId());
        assertEquals(name, dto.getName());
        assertEquals(headBranch, dto.getHeadBranch());
        assertEquals(status, dto.getStatus());
        assertEquals(conclusion, dto.getConclusion());
        assertEquals(now, dto.getCreatedAt());
        assertEquals(now, dto.getUpdatedAt());
    }
}
