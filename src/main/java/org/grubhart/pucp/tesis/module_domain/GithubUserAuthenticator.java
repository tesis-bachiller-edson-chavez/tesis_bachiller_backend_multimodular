package org.grubhart.pucp.tesis.module_domain;

/**
 * Defines the contract for authenticating a user against GitHub.
 * This interface resides in the domain module to act as a common contract
 * for any module that needs to verify user membership without depending
 * on the implementation details of the collector module.
 */
public interface GithubUserAuthenticator {

    /**
     * Checks if a given user is a member of a specific GitHub organization.
     *
     * @param username The GitHub username to check.
     * @param organization The name of the GitHub organization.
     * @return true if the user is a public member of the organization, false otherwise.
     */
    boolean isUserMemberOfOrganization(String username, String organization);
}
