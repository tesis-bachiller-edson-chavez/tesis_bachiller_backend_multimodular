package org.grubhart.pucp.tesis.module_administration;

/**
 * Define el contrato para un cliente que interactúa con la API de GitHub
 * para verificar la membresía de un usuario en una organización.
 */
public interface GithubClient {
    boolean isUserMemberOfOrganization(String username, String organizationName);
}