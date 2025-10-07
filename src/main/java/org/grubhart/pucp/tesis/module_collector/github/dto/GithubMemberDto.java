package org.grubhart.pucp.tesis.module_collector.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubMemberDto(
        Long id,
        String login,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
