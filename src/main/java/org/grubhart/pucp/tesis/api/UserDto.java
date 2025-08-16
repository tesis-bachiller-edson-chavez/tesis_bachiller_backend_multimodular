package org.grubhart.pucp.tesis.api;

import java.util.Set;

public record UserDto(Long id, String githubUsername, String email, Set<String> roles) {}