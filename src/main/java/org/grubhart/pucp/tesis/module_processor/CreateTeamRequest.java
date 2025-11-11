package org.grubhart.pucp.tesis.module_processor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for creating a new team
 */
public class CreateTeamRequest {

    @NotBlank(message = "Team name is required")
    @Size(min = 2, max = 100, message = "Team name must be between 2 and 100 characters")
    private String name;

    private List<Long> techLeadIds;

    public CreateTeamRequest() {
    }

    public CreateTeamRequest(String name, List<Long> techLeadIds) {
        this.name = name;
        this.techLeadIds = techLeadIds;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Long> getTechLeadIds() {
        return techLeadIds;
    }

    public void setTechLeadIds(List<Long> techLeadIds) {
        this.techLeadIds = techLeadIds;
    }
}
