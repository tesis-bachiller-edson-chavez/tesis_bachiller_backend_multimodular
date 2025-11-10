package org.grubhart.pucp.tesis.module_domain;

/**
 * Contrato para obtener información de servicios de Datadog.
 * Expone métodos para consultar la API de Datadog sin exponer credenciales al frontend.
 */
public interface DatadogServiceCollector {
    /**
     * Obtiene la lista de servicios disponibles en Datadog APM.
     *
     * @return DatadogServicesResponse conteniendo la lista de servicios
     */
    DatadogServicesResponse getServices();
}
