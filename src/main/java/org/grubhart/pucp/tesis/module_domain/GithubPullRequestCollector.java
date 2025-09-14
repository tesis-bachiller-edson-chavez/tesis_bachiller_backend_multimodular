package org.grubhart.pucp.tesis.module_domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Define el contrato para un componente capaz de recolectar Pull Requests desde GitHub.
 */
public interface GithubPullRequestCollector {

    /**
     * Obtiene los Pull Requests de un repositorio específico que han sido actualizados desde una fecha dada.
     *
     * @param owner El propietario del repositorio.
     * @param repo El nombre del repositorio.
     * @param since La fecha desde la cual buscar Pull Requests.
     * @return Una lista de DTOs que representan los Pull Requests encontrados.
     */
    List<GithubPullRequestDto> getPullRequests(String owner, String repo, LocalDateTime since);
}
