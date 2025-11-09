package org.grubhart.pucp.tesis.module_domain;

import java.util.List;

public interface GithubRepositoryCollector {
    List<GithubRepositoryDto> getOrgRepositories(String organizationName);
}
