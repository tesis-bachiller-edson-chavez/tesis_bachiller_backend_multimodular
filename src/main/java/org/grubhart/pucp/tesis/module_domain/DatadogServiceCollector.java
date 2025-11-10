package org.grubhart.pucp.tesis.module_domain;

/**
 * Domain interface for collecting Datadog services.
 * Abstraction that allows fetching service information without depending on specific implementation.
 */
public interface DatadogServiceCollector {

    /**
     * Fetches all service definitions from Datadog Service Catalog.
     *
     * @return DatadogServicesResponse containing the list of services
     */
    DatadogServicesResponse getServices();
}
