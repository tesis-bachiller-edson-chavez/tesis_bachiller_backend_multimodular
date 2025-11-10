package org.grubhart.pucp.tesis.module_domain;

import java.util.List;

/**
 * Interfaz del dominio para recolectar repositorios desde GitHub.
 * Abstracción que permite obtener información de repositorios sin depender de la implementación específica.
 */
public interface GithubRepositoryCollector {

    /**
     * Obtiene la lista de repositorios de una organización en GitHub.
     *
     * @param organizationName Nombre de la organización en GitHub
     * @return Lista de repositorios de la organización
     */
    List<GithubRepositoryDto> getOrgRepositories(String organizationName);
}
