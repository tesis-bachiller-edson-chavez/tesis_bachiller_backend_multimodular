package org.grubhart.pucp.tesis.module_api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for assigning a member to a team
 */
public class AssignMemberRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    public AssignMemberRequest() {
    }

    public AssignMemberRequest(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
