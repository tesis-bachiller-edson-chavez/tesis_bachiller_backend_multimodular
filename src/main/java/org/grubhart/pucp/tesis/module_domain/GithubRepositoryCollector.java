package org.grubhart.pucp.tesis.module_domain;

import java.util.List;

/**
 * Interfaz del dominio para recolectar repositorios desde GitHub.
 * Abstracción que permite obtener información de repositorios sin depender de la implementación específica.
 */
public interface GithubRepositoryCollector {

    /**
     * Obtiene la lista de repositorios del usuario autenticado.
     *
     * @return Lista de repositorios accesibles por el usuario autenticado
     */
    List<GithubRepositoryDto> getUserRepositories();
}