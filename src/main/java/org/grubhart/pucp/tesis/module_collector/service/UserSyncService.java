package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.OrganizationMember;
import org.grubhart.pucp.tesis.module_domain.User;
import org.grubhart.pucp.tesis.module_domain.UserRepository;
import org.grubhart.pucp.tesis.module_domain.GithubUserCollector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserSyncService {

    private final GithubUserCollector githubUserCollector;
    private final UserRepository userRepository;
    private final String organizationName;

    public UserSyncService(GithubUserCollector githubUserCollector,
                           UserRepository userRepository,
                           @Value("${dora.github.organization-name}") String organizationName) {
        this.githubUserCollector = githubUserCollector;
        this.userRepository = userRepository;
        this.organizationName = organizationName;
    }

    public void synchronizeUsers(String organizationName) {
        // 1. Get remote users from GitHub and create a lookup map
        List<OrganizationMember> githubMembers = githubUserCollector.getOrganizationMembers(organizationName);
        Map<Long, OrganizationMember> githubMembersMap = githubMembers.stream()
                .collect(Collectors.toMap(OrganizationMember::id, Function.identity()));

        // 2. Get all local users (active and inactive) and create a lookup map
        List<User> localUsers = userRepository.findAll();
        Map<Long, User> localUsersByGithubId = localUsers.stream()
                .collect(Collectors.toMap(User::getGithubId, Function.identity()));

        List<User> usersToSave = new ArrayList<>();

        // 3. Identify users to create, update, and deactivate
        for (OrganizationMember githubMember : githubMembers) {
            User localUser = localUsersByGithubId.get(githubMember.id());
            if (localUser == null) {
                // New user, create it
                User newUser = new User();
                newUser.setGithubId(githubMember.id());
                newUser.setGithubUsername(githubMember.login());
                newUser.setAvatarUrl(githubMember.avatarUrl());
                newUser.setActive(true);
                usersToSave.add(newUser);
            } else {
                // Existing user, update info and ensure it's active
                localUser.setGithubUsername(githubMember.login());
                localUser.setAvatarUrl(githubMember.avatarUrl());
                localUser.setActive(true);
                usersToSave.add(localUser);
            }
        }

        // 4. Deactivate users that are no longer in the GitHub organization
        for (User localUser : localUsers) {
            if (localUser.isActive() && !githubMembersMap.containsKey(localUser.getGithubId())) {
                localUser.setActive(false);
                usersToSave.add(localUser);
            }
        }

        // 5. Save all changes in a single transaction
        if (!usersToSave.isEmpty()) {
            userRepository.saveAll(usersToSave);
        }
    }

    @Scheduled(initialDelay = 20000, fixedRate = Long.MAX_VALUE) // Executes at 2 AM every day
    public void scheduledSync() {
        synchronizeUsers(organizationName);
    }
}
