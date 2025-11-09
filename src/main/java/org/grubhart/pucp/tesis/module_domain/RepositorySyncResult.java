package org.grubhart.pucp.tesis.module_domain;

/**
 * Resultado de la sincronización de repositorios.
 *
 * @param newRepositories Cantidad de repositorios nuevos creados
 * @param totalRepositories Total de repositorios en la base de datos después de la sincronización
 * @param unchanged Cantidad de repositorios que ya existían y no fueron modificados
 */
public record RepositorySyncResult(
        int newRepositories,
        int totalRepositories,
        int unchanged
) {
}