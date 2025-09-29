
package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class GitHubWorkflowRunsResponse {

    @JsonProperty("total_count")
    private int totalCount;

    @JsonProperty("workflow_runs")
    private List<GitHubWorkflowRunDto> workflowRuns;

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public List<GitHubWorkflowRunDto> getWorkflowRuns() {
        return workflowRuns;
    }

    public void setWorkflowRuns(List<GitHubWorkflowRunDto> workflowRuns) {
        this.workflowRuns = workflowRuns;
    }
}
