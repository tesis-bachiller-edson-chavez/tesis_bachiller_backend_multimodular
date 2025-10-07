package org.grubhart.pucp.tesis.module_collector.github.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GithubMemberDtoTest {

    @Test
    void testGithubMemberDto() {
        // Given
        Long expectedId = 12345L;
        String expectedLogin = "testuser";
        String expectedAvatarUrl = "http://example.com/avatar.png";

        // When
        GithubMemberDto memberDto = new GithubMemberDto(expectedId, expectedLogin, expectedAvatarUrl);

        // Then
        assertEquals(expectedId, memberDto.id());
        assertEquals(expectedLogin, memberDto.login());
        assertEquals(expectedAvatarUrl, memberDto.avatarUrl());
    }
}
