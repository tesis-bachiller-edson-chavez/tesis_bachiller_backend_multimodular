package org.grubhart.pucp.tesis.module_domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO que representa un repositorio obtenido desde la API de GitHub.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubRepositoryDto(
        @JsonProperty("id") Long id,
        @JsonProperty("name") String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("private") boolean isPrivate,
        @JsonProperty("owner") Owner owner
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Owner(
            @JsonProperty("login") String login
    ) {}
}