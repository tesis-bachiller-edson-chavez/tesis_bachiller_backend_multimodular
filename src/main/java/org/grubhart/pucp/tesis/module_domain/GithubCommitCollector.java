package org.grubhart.pucp.tesis.module_domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Defines the contract for collecting commits from GitHub.
 * This interface resides in the domain module to act as a common contract
 * for any module that needs to fetch commit data.
 */
public interface GithubCommitCollector {

    /**
     * Retrieves a list of commits from a repository since a given point in time.
     *
     * @param owner The owner of the repository.
     * @param repo The name of the repository.
     * @param since The date and time from which to fetch commits.
     * @return A list of {@link GithubCommitDto} objects representing the commits.
     */
    List<GithubCommitDto> getCommits(String owner, String repo, LocalDateTime since);
}
