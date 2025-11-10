package org.grubhart.pucp.tesis.module_domain;


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class RepositoryConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String repositoryUrl;

    /**
     * The Datadog service name associated with this repository.
     * This should match the DD_SERVICE environment variable used in deployment.
     * Used to filter incidents from Datadog API for this specific service.
     * If null, incidents won't be synchronized for this repository.
     */
    private String datadogServiceName;

    /**
     * The file name of the GitHub Actions workflow that handles deployments.
     * Example: "deploy.yml"
     */
    private String deploymentWorkflowFileName;



    protected RepositoryConfig() {
    }

    public RepositoryConfig(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public RepositoryConfig(String repositoryUrl, String datadogServiceName) {
        this.repositoryUrl = repositoryUrl;
        this.datadogServiceName = datadogServiceName;
    }

    public Long getId() {
        return id;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getOwner() {
        if (repositoryUrl == null) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(repositoryUrl.trim());
            String path = uri.getPath();

            if (path == null || path.isEmpty() || path.equals("/")) {
                return null;
            }

            // Elimina la barra inicial y final para un split limpio
            String trimmedPath = path.startsWith("/") ? path.substring(1) : path;
            trimmedPath = trimmedPath.endsWith("/") ? trimmedPath.substring(0, trimmedPath.length() - 1) : trimmedPath;

            if (trimmedPath.isEmpty()) {
                return null;
            }

            String[] parts = trimmedPath.split("/");
            if (!parts[0].isBlank()) {
                return parts[0];
            }
        } catch (java.net.URISyntaxException e) {
            // Si la URL está mal formada, no se puede parsear.
            return null;
        }
        return null;
    }

    public String getDeploymentWorkflowFileName(){
        return  deploymentWorkflowFileName;
    }

    public void setDeploymentWorkflowFileName(String deploymentWorkflowFileName) {
        this.deploymentWorkflowFileName = deploymentWorkflowFileName;
    }

    public String getRepoName() {
        if (repositoryUrl == null) {
            return null;
        }
        try {
            java.net.URI uri = new java.net.URI(repositoryUrl.trim());
            String path = uri.getPath();

            if (path == null || path.isEmpty() || path.equals("/")) {
                return null;
            }

            // Elimina la barra inicial y final para un split limpio
            String trimmedPath = path.startsWith("/") ? path.substring(1) : path;
            trimmedPath = trimmedPath.endsWith("/") ? trimmedPath.substring(0, trimmedPath.length() - 1) : trimmedPath;

            if (trimmedPath.isEmpty()) {
                return null;
            }

            String[] parts = trimmedPath.split("/");
            if (parts.length >= 2 && !parts[1].isBlank()) {
                return parts[1];
            }
        } catch (java.net.URISyntaxException e) {
            // Si la URL está mal formada, no se puede parsear.
            return null;
        }
        return null;
    }

    public String getDatadogServiceName() {
        return datadogServiceName;
    }

    public void setDatadogServiceName(String datadogServiceName) {
        this.datadogServiceName = datadogServiceName;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }
}
