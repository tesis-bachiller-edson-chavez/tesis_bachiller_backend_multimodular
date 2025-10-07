package org.grubhart.pucp.tesis.module_domain;

import java.util.List;

public interface GithubUserCollector {
    List<OrganizationMember> getOrganizationMembers(String organizationName);
}
