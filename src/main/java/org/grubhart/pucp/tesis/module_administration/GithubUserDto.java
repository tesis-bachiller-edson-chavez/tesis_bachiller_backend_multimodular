package org.grubhart.pucp.tesis.module_administration;

// Un record es perfecto para un DTO inmutable y no requiere Lombok.
public record GithubUserDto(Long id, String username, String email) {}