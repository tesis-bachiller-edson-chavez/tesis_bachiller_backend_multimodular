package org.grubhart.pucp.tesis.module_domain;

import java.time.LocalDateTime;
import java.util.List;

public interface GithubDeploymentCollector {
    List<GitHubWorkflowRunDto> getWorkflowRuns(String owner, String repo, String workflowFileName, LocalDateTime since);
}
