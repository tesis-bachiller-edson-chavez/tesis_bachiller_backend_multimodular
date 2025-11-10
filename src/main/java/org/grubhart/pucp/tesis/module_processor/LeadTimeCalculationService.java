package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeadTimeCalculationService {

    private static final String PRODUCTION_ENVIRONMENT = "production";
    private static final Sort SORT_BY_CREATED_AT_ASC = Sort.by(Sort.Direction.ASC, "createdAt");
    private static final Sort SORT_BY_CREATED_AT_DESC = Sort.by(Sort.Direction.DESC, "createdAt");

    private final DeploymentRepository deploymentRepository;
    private final CommitRepository commitRepository;
    private final ChangeLeadTimeRepository changeLeadTimeRepository;

    public LeadTimeCalculationService(DeploymentRepository deploymentRepository,
                                      CommitRepository commitRepository,
                                      ChangeLeadTimeRepository changeLeadTimeRepository) {
        this.deploymentRepository = deploymentRepository;
        this.commitRepository = commitRepository;
        this.changeLeadTimeRepository = changeLeadTimeRepository;
    }

    @Transactional
    public void calculate() {
        List<Deployment> unprocessedDeployments = deploymentRepository.findByLeadTimeProcessedFalseAndEnvironment(PRODUCTION_ENVIRONMENT, SORT_BY_CREATED_AT_ASC);
        List<Deployment> processedDeployments = new ArrayList<>();

        for (Deployment currentDeployment : unprocessedDeployments) {
            Long repositoryId = currentDeployment.getRepository().getId();

            Optional<Deployment> previousDeploymentOpt = deploymentRepository.findFirstByRepositoryIdAndEnvironmentAndCreatedAtBefore(
                    repositoryId, PRODUCTION_ENVIRONMENT, currentDeployment.getCreatedAt(), SORT_BY_CREATED_AT_DESC);

            // 1. Get all commits from the previous deployment to use as boundary
            Set<String> previousDeploymentCommitShas = previousDeploymentOpt
                    .map(prevDep -> getAllCommitsForDeployment(prevDep, repositoryId))
                    .orElse(Collections.emptySet());

            // 2. Traverse the graph backwards from the current deployment's commit
            Set<Commit> commitsToProcess = getAllCommitsForDeployment(currentDeployment, repositoryId, previousDeploymentCommitShas);


            if (!commitsToProcess.isEmpty()) {
                // 3. Calculate lead time for all new commits
                List<ChangeLeadTime> leadTimes = commitsToProcess.stream()
                        .map(commit -> {
                            long leadTimeInSeconds = Duration.between(commit.getDate(), currentDeployment.getCreatedAt()).getSeconds();
                            return new ChangeLeadTime(commit, currentDeployment, leadTimeInSeconds);
                        })
                        .collect(Collectors.toList());
                changeLeadTimeRepository.saveAll(leadTimes);
            }

            currentDeployment.setLeadTimeProcessed(true);
            processedDeployments.add(currentDeployment);
        }

        if (!processedDeployments.isEmpty()) {
            deploymentRepository.saveAll(processedDeployments);
        }
    }

    private Set<String> getAllCommitsForDeployment(Deployment deployment, Long repositoryId) {
        Set<Commit> commits = getAllCommitsForDeployment(deployment, repositoryId, Collections.emptySet());
        return commits.stream()
                .map(Commit::getSha)
                .collect(Collectors.toSet());
    }

    private Set<Commit> getAllCommitsForDeployment(Deployment deployment, Long repositoryId, Set<String> boundaryCommitShas) {
        Set<Commit> commitsInDeployment = new HashSet<>();
        Queue<String> commitsToVisit = new LinkedList<>();
        Set<String> visitedShas = new HashSet<>();

        commitRepository.findByRepositoryIdAndSha(repositoryId, deployment.getSha()).ifPresent(initialCommit -> {
            commitsToVisit.add(initialCommit.getSha());
            visitedShas.add(initialCommit.getSha());
        });

        while (!commitsToVisit.isEmpty()) {
            String currentSha = commitsToVisit.poll();
            if (boundaryCommitShas.contains(currentSha)) {
                continue; // Stop traversing this path
            }

            Optional<Commit> commitOpt = commitRepository.findByRepositoryIdAndSha(repositoryId, currentSha);
            if (commitOpt.isPresent()) {
                Commit currentCommit = commitOpt.get();
                commitsInDeployment.add(currentCommit);

                if (currentCommit.getParents() != null) {
                    for (Commit parent : currentCommit.getParents()) {
                        if (!visitedShas.contains(parent.getSha())) {
                            commitsToVisit.add(parent.getSha());
                            visitedShas.add(parent.getSha());
                        }
                    }
                }
            }
        }
        return commitsInDeployment;
    }
}
