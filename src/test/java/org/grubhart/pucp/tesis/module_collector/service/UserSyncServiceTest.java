package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.OrganizationMember;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
import org.grubhart.pucp.tesis.module_domain.GithubUserCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    @Mock
    private GithubUserCollector githubUserCollector;

    @Mock
    private UserRepository userRepository;

    private UserSyncService userSyncService;

    @Captor
    private ArgumentCaptor<List<User>> userListCaptor;

    @BeforeEach
    void setUp() {
        userSyncService = new UserSyncService(githubUserCollector, userRepository, "test-org");
    }

    @Test
    void synchronizeUsers_shouldCreateNewUsers_whenTheyDoNotExistLocally() {
        // GIVEN
        var githubMember = new OrganizationMember(1L, "testuser", "http://avatar.url");
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(List.of(githubMember));
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, times(1)).saveAll(userListCaptor.capture());

        List<User> savedUsers = userListCaptor.getValue();
        assertEquals(1, savedUsers.size());
        User savedUser = savedUsers.get(0);

        assertEquals(githubMember.id(), savedUser.getGithubId());
        assertEquals(githubMember.login(), savedUser.getGithubUsername());
        assertEquals(githubMember.avatarUrl(), savedUser.getAvatarUrl());
        assertTrue(savedUser.isActive());
    }

    @Test
    void synchronizeUsers_shouldDeactivateUsers_whenTheyAreNoLongerInTheOrganization() {
        // GIVEN
        User existingUser = new User();
        existingUser.setId(100L);
        existingUser.setGithubId(1L);
        existingUser.setGithubUsername("existingUser");
        existingUser.setAvatarUrl("http://avatar.url");
        existingUser.setActive(true);

        when(userRepository.findAll()).thenReturn(List.of(existingUser));
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(Collections.emptyList());

        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, times(1)).saveAll(userListCaptor.capture());

        List<User> updatedUsers = userListCaptor.getValue();
        assertEquals(1, updatedUsers.size());
        User updatedUser = updatedUsers.get(0);
        
        assertEquals(existingUser.getId(), updatedUser.getId());
        assertFalse(updatedUser.isActive());
    }

    @Test
    void synchronizeUsers_shouldUpdateExistingUserIfFound() {
        // GIVEN
        var githubMember = new OrganizationMember(1L, "testuser", "http://new-avatar.url");
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(List.of(githubMember));

        User existingUser = new User();
        existingUser.setId(100L);
        existingUser.setGithubId(1L);
        existingUser.setGithubUsername("testuser");
        existingUser.setAvatarUrl("http://old-avatar.url");
        existingUser.setActive(true);

        when(userRepository.findAll()).thenReturn(List.of(existingUser));


        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, times(1)).saveAll(userListCaptor.capture());
        List<User> savedUsers = userListCaptor.getValue();
        assertEquals(1, savedUsers.size());
        User savedUser = savedUsers.get(0);


        assertEquals(existingUser.getId(), savedUser.getId());
        assertEquals("http://new-avatar.url", savedUser.getAvatarUrl());
        assertTrue(savedUser.isActive());
    }

    @Test
    void synchronizeUsers_shouldNotDeactivateAlreadyInactiveUser() {
        // GIVEN
        User inactiveUser = new User();
        inactiveUser.setId(101L);
        inactiveUser.setGithubId(2L);
        inactiveUser.setGithubUsername("inactiveUser");
        inactiveUser.setActive(false);

        when(userRepository.findAll()).thenReturn(List.of(inactiveUser));
        when(githubUserCollector.getOrganizationMembers(anyString())).thenReturn(Collections.emptyList());

        // WHEN
        userSyncService.synchronizeUsers("some-org");

        // THEN
        verify(userRepository, never()).saveAll(userListCaptor.capture());
    }

    @Test
    void scheduledSync_shouldTriggerSynchronizationWithConfiguredOrganization() {
        // GIVEN
        // No specific setup needed, the service is already configured in setUp()

        // WHEN
        userSyncService.scheduledSync();

        // THEN
        verify(githubUserCollector, times(1)).getOrganizationMembers("test-org");
    }
}
