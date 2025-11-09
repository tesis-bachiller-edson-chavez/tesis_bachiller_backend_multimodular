package org.grubhart.pucp.tesis.module_domain;

/**
 * Contrato para sincronizar repositorios desde GitHub a la base de datos local.
 */
public interface RepositorySyncService {
    /**
     * Sincroniza repositorios desde GitHub a la base de datos de forma idempotente.
     *
     * @return Resultado de la sincronización con estadísticas
     */
    RepositorySyncResult synchronizeRepositories();
}
