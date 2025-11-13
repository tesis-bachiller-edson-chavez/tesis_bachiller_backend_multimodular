package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para obtener métricas del dashboard específicas para el rol Tech Lead.
 * Este servicio agrega métricas de todos los miembros del equipo del tech lead.
 */
@Service
public class TechLeadDashboardService {

    private static final Logger logger = LoggerFactory.getLogger(TechLeadDashboardService.class);
    private static final long INCIDENT_CORRELATION_WINDOW_HOURS = 48;

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final CommitRepository commitRepository;
    private final ChangeLeadTimeRepository changeLeadTimeRepository;
    private final IncidentRepository incidentRepository;
    private final PullRequestRepository pullRequestRepository;
    private final CommitParentRepository commitParentRepository;

    public TechLeadDashboardService(UserRepository userRepository,
                                    TeamRepository teamRepository,
                                    CommitRepository commitRepository,
                                    ChangeLeadTimeRepository changeLeadTimeRepository,
                                    IncidentRepository incidentRepository,
                                    PullRequestRepository pullRequestRepository,
                                    CommitParentRepository commitParentRepository) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.commitRepository = commitRepository;
        this.changeLeadTimeRepository = changeLeadTimeRepository;
        this.incidentRepository = incidentRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.commitParentRepository = commitParentRepository;
    }

    /**
     * Obtiene las métricas completas del dashboard para un tech lead.
     * Incluye métricas agregadas de todos los miembros del equipo.
     *
     * @param techLeadGithubUsername El nombre de usuario de GitHub del tech lead
     * @param startDate Fecha de inicio del rango (basado en deployment.createdAt), opcional
     * @param endDate Fecha de fin del rango (basado en deployment.createdAt), opcional
     * @param repositoryIds Lista de IDs de repositorios para filtrar, opcional
     * @param memberIds Lista de IDs de miembros para filtrar, opcional (null = todos los miembros)
     * @return TechLeadMetricsResponse con todas las métricas agregadas del equipo
     */
    public TechLeadMetricsResponse getTechLeadMetrics(String techLeadGithubUsername,
                                                      LocalDate startDate,
                                                      LocalDate endDate,
                                                      List<Long> repositoryIds,
                                                      List<Long> memberIds) {
        logger.info("Obteniendo métricas para el tech lead: {} (startDate: {}, endDate: {}, repositoryIds: {}, memberIds: {})",
                techLeadGithubUsername, startDate, endDate, repositoryIds, memberIds);

        // Obtener el usuario tech lead
        User techLead = userRepository.findByGithubUsernameIgnoreCase(techLeadGithubUsername)
                .orElseThrow(() -> new IllegalArgumentException("Tech lead no encontrado: " + techLeadGithubUsername));

        if (techLead.getTeamId() == null) {
            logger.warn("El tech lead {} no tiene un equipo asignado", techLeadGithubUsername);
            throw new IllegalArgumentException("El tech lead no tiene un equipo asignado");
        }

        // Obtener el equipo
        Team team = teamRepository.findById(techLead.getTeamId())
                .orElseThrow(() -> new IllegalArgumentException("Equipo no encontrado: " + techLead.getTeamId()));

        // Obtener todos los miembros del equipo
        List<User> allTeamMembers = userRepository.findByTeamId(team.getId());

        if (allTeamMembers.isEmpty()) {
            logger.warn("El equipo {} no tiene miembros", team.getName());
            return createEmptyMetricsResponse(techLeadGithubUsername, team);
        }

        // Filtrar miembros según memberIds si se especifica
        List<User> filteredMembers = filterMembers(allTeamMembers, memberIds);

        if (filteredMembers.isEmpty()) {
            logger.warn("No se encontraron miembros después de aplicar el filtro memberIds: {}", memberIds);
            return createEmptyMetricsResponse(techLeadGithubUsername, team);
        }

        logger.debug("Se encontraron {} miembros en el equipo (filtrados: {})",
                allTeamMembers.size(), filteredMembers.size());

        // Obtener commits de todos los miembros filtrados
        List<Commit> teamCommits = getTeamCommits(filteredMembers);

        if (teamCommits.isEmpty()) {
            logger.warn("No se encontraron commits para los miembros del equipo");
            return createEmptyMetricsResponse(techLeadGithubUsername, team);
        }

        // Filtrar commits basándose en deployments (fecha y repositorio)
        List<Commit> filteredCommits = filterCommitsByDeployments(teamCommits, startDate, endDate, repositoryIds);

        logger.debug("Después de aplicar filtros: {} commits (de {} totales)",
                filteredCommits.size(), teamCommits.size());

        // Calcular estadísticas por miembro
        List<TeamMemberStatsDto> memberStats = calculateMemberStats(filteredMembers, filteredCommits,
                startDate, endDate, repositoryIds);

        // Agrupar commits filtrados por repositorio
        Map<RepositoryConfig, List<Commit>> commitsByRepository = filteredCommits.stream()
                .collect(Collectors.groupingBy(Commit::getRepository));

        // Crear estadísticas por repositorio
        List<RepositoryStatsDto> repositoryStats = commitsByRepository.entrySet().stream()
                .map(entry -> {
                    RepositoryConfig repo = entry.getKey();
                    long commitCount = entry.getValue().size();
                    return new RepositoryStatsDto(
                            repo.getId(),
                            repo.getRepoName(),
                            repo.getRepositoryUrl(),
                            commitCount
                    );
                })
                .sorted(Comparator.comparing(RepositoryStatsDto::commitCount).reversed())
                .collect(Collectors.toList());

        // Calcular estadísticas agregadas de commits
        CommitStatsDto commitStats = calculateCommitStats(filteredCommits, commitsByRepository.size());

        // Calcular estadísticas de Pull Requests
        PullRequestStatsDto pullRequestStats = calculatePullRequestStats(filteredCommits);

        // Calcular métricas DORA
        DeveloperDoraMetricsDto doraMetrics = calculateDoraMetrics(filteredCommits, startDate, endDate, repositoryIds);

        logger.info("Métricas calculadas exitosamente para el tech lead: {}. Equipo: {}, Miembros: {}, " +
                        "Total commits: {}, Repositorios: {}, PRs: {}, Lead Time promedio: {} horas",
                techLeadGithubUsername, team.getName(), memberStats.size(),
                commitStats.totalCommits(), repositoryStats.size(),
                pullRequestStats.totalPullRequests(), doraMetrics.averageLeadTimeHours());

        return new TechLeadMetricsResponse(
                techLeadGithubUsername,
                team.getId(),
                team.getName(),
                memberStats,
                repositoryStats,
                commitStats,
                pullRequestStats,
                doraMetrics
        );
    }

    /**
     * Filtra la lista de miembros según los IDs especificados.
     * Si memberIds es null o vacío, retorna todos los miembros.
     */
    private List<User> filterMembers(List<User> allMembers, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return allMembers;
        }

        Set<Long> memberIdSet = new HashSet<>(memberIds);
        return allMembers.stream()
                .filter(member -> memberIdSet.contains(member.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los commits de una lista de usuarios.
     */
    private List<Commit> getTeamCommits(List<User> members) {
        Set<String> memberUsernames = members.stream()
                .map(User::getGithubUsername)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return commitRepository.findAll().stream()
                .filter(commit -> memberUsernames.contains(commit.getAuthor().toLowerCase()))
                .collect(Collectors.toList());
    }

    /**
     * Calcula estadísticas individuales para cada miembro del equipo.
     */
    private List<TeamMemberStatsDto> calculateMemberStats(List<User> members,
                                                           List<Commit> filteredCommits,
                                                           LocalDate startDate,
                                                           LocalDate endDate,
                                                           List<Long> repositoryIds) {
        return members.stream()
                .map(member -> {
                    String username = member.getGithubUsername().toLowerCase();

                    // Filtrar commits del miembro
                    List<Commit> memberCommits = filteredCommits.stream()
                            .filter(commit -> commit.getAuthor().toLowerCase().equals(username))
                            .collect(Collectors.toList());

                    long totalCommits = memberCommits.size();

                    // Calcular PRs del miembro
                    PullRequestStatsDto memberPRStats = calculatePullRequestStats(memberCommits);

                    // Calcular lead time promedio del miembro
                    Double averageLeadTime = calculateAverageLeadTime(memberCommits, startDate, endDate, repositoryIds);

                    // Contar deployments del miembro
                    long deploymentCount = countDeployments(memberCommits, startDate, endDate, repositoryIds);

                    return new TeamMemberStatsDto(
                            member.getId(),
                            member.getGithubUsername(),
                            member.getName(),
                            member.getEmail(),
                            totalCommits,
                            memberPRStats.totalPullRequests(),
                            memberPRStats.mergedPullRequests(),
                            averageLeadTime,
                            deploymentCount
                    );
                })
                .sorted(Comparator.comparing(TeamMemberStatsDto::totalCommits).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Calcula el lead time promedio para una lista de commits.
     */
    private Double calculateAverageLeadTime(List<Commit> commits,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            List<Long> repositoryIds) {
        if (commits.isEmpty()) {
            return null;
        }

        Set<String> commitShas = commits.stream()
                .map(Commit::getSha)
                .collect(Collectors.toSet());

        List<ChangeLeadTime> leadTimes = changeLeadTimeRepository.findAll().stream()
                .filter(lt -> commitShas.contains(lt.getCommit().getSha()))
                .filter(lt -> applyDeploymentDateFilter(lt.getDeployment(), startDate, endDate))
                .filter(lt -> applyRepositoryFilter(lt.getDeployment(), repositoryIds))
                .collect(Collectors.toList());

        if (leadTimes.isEmpty()) {
            return null;
        }

        return leadTimes.stream()
                .mapToDouble(lt -> lt.getLeadTimeInSeconds() / 3600.0)
                .average()
                .orElse(0.0);
    }

    /**
     * Cuenta la cantidad de deployments únicos para una lista de commits.
     */
    private long countDeployments(List<Commit> commits,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   List<Long> repositoryIds) {
        if (commits.isEmpty()) {
            return 0;
        }

        Set<String> commitShas = commits.stream()
                .map(Commit::getSha)
                .collect(Collectors.toSet());

        return changeLeadTimeRepository.findAll().stream()
                .filter(lt -> commitShas.contains(lt.getCommit().getSha()))
                .filter(lt -> applyDeploymentDateFilter(lt.getDeployment(), startDate, endDate))
                .filter(lt -> applyRepositoryFilter(lt.getDeployment(), repositoryIds))
                .map(lt -> lt.getDeployment().getId())
                .distinct()
                .count();
    }

    /**
     * Calcula estadísticas agregadas de commits.
     */
    private CommitStatsDto calculateCommitStats(List<Commit> commits, int repositoryCount) {
        long totalCommits = commits.size();

        LocalDateTime lastCommitDate = commits.stream()
                .map(Commit::getDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime firstCommitDate = commits.stream()
                .map(Commit::getDate)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        return new CommitStatsDto(
                totalCommits,
                (long) repositoryCount,
                lastCommitDate,
                firstCommitDate
        );
    }

    /**
     * Calcula estadísticas de Pull Requests.
     * Reutiliza la lógica del DeveloperDashboardService.
     */
    private PullRequestStatsDto calculatePullRequestStats(List<Commit> commits) {
        if (commits.isEmpty()) {
            return new PullRequestStatsDto(0L, 0L, 0L);
        }

        Set<String> commitShas = commits.stream()
                .map(Commit::getSha)
                .collect(Collectors.toSet());

        Map<String, List<String>> parentToChildren = buildParentToChildrenMap();
        List<PullRequest> allPullRequests = pullRequestRepository.findAll();

        List<PullRequest> relevantPullRequests = allPullRequests.stream()
                .filter(pr -> {
                    if (pr.getFirstCommitSha() == null) {
                        return false;
                    }
                    Set<String> prCommits = findAllDescendants(pr.getFirstCommitSha(), parentToChildren);
                    prCommits.add(pr.getFirstCommitSha());
                    return prCommits.stream().anyMatch(commitShas::contains);
                })
                .collect(Collectors.toList());

        long totalPullRequests = relevantPullRequests.size();
        long mergedPullRequests = relevantPullRequests.stream()
                .filter(pr -> "closed".equalsIgnoreCase(pr.getState()) && pr.getMergedAt() != null)
                .count();
        long openPullRequests = relevantPullRequests.stream()
                .filter(pr -> "open".equalsIgnoreCase(pr.getState()))
                .count();

        return new PullRequestStatsDto(totalPullRequests, mergedPullRequests, openPullRequests);
    }

    /**
     * Construye un mapa de parent SHA -> lista de children SHAs.
     */
    private Map<String, List<String>> buildParentToChildrenMap() {
        List<CommitParent> allCommitParents = commitParentRepository.findAll();
        Map<String, List<String>> parentToChildren = new HashMap<>();

        for (CommitParent cp : allCommitParents) {
            String parentSha = cp.getParent().getSha();
            String childSha = cp.getCommit().getSha();
            parentToChildren.computeIfAbsent(parentSha, k -> new ArrayList<>()).add(childSha);
        }

        return parentToChildren;
    }

    /**
     * Encuentra todos los commits descendientes de un commit dado.
     */
    private Set<String> findAllDescendants(String startSha, Map<String, List<String>> parentToChildren) {
        Set<String> descendants = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startSha);

        while (!queue.isEmpty()) {
            String currentSha = queue.poll();
            List<String> children = parentToChildren.get(currentSha);

            if (children != null) {
                for (String childSha : children) {
                    if (!descendants.contains(childSha)) {
                        descendants.add(childSha);
                        queue.add(childSha);
                    }
                }
            }
        }

        return descendants;
    }

    /**
     * Calcula métricas DORA agregadas del equipo.
     */
    private DeveloperDoraMetricsDto calculateDoraMetrics(List<Commit> commits,
                                                         LocalDate startDate,
                                                         LocalDate endDate,
                                                         List<Long> repositoryIds) {
        if (commits.isEmpty()) {
            return new DeveloperDoraMetricsDto(
                    null, null, null,
                    0L, 0L,
                    null, 0L,
                    Collections.emptyList()
            );
        }

        Set<String> commitShas = commits.stream()
                .map(Commit::getSha)
                .collect(Collectors.toSet());

        List<ChangeLeadTime> leadTimes = changeLeadTimeRepository.findAll().stream()
                .filter(lt -> commitShas.contains(lt.getCommit().getSha()))
                .filter(lt -> applyDeploymentDateFilter(lt.getDeployment(), startDate, endDate))
                .filter(lt -> applyRepositoryFilter(lt.getDeployment(), repositoryIds))
                .collect(Collectors.toList());

        if (leadTimes.isEmpty()) {
            return new DeveloperDoraMetricsDto(
                    null, null, null,
                    0L, 0L,
                    null, 0L,
                    Collections.emptyList()
            );
        }

        DoubleSummaryStatistics leadTimeStats = leadTimes.stream()
                .mapToDouble(lt -> lt.getLeadTimeInSeconds() / 3600.0)
                .summaryStatistics();

        double averageLeadTimeHours = leadTimeStats.getAverage();
        double minLeadTimeHours = leadTimeStats.getMin();
        double maxLeadTimeHours = leadTimeStats.getMax();
        long deploymentCommitCount = leadTimes.size();

        Set<Long> uniqueDeploymentIds = leadTimes.stream()
                .map(lt -> lt.getDeployment().getId())
                .collect(Collectors.toSet());
        long totalDeploymentCount = uniqueDeploymentIds.size();

        List<Deployment> deployments = leadTimes.stream()
                .map(ChangeLeadTime::getDeployment)
                .distinct()
                .collect(Collectors.toList());

        List<Incident> allIncidents = incidentRepository.findAll();
        Set<Long> failedDeploymentIds = identifyFailedDeployments(deployments, allIncidents);
        long failedDeploymentCount = failedDeploymentIds.size();

        Double changeFailureRate = totalDeploymentCount > 0
                ? (failedDeploymentCount * 100.0) / totalDeploymentCount
                : null;

        List<DailyMetricDto> dailyMetrics = calculateDailyTimeSeries(leadTimes, failedDeploymentIds);

        return new DeveloperDoraMetricsDto(
                averageLeadTimeHours,
                minLeadTimeHours,
                maxLeadTimeHours,
                totalDeploymentCount,
                deploymentCommitCount,
                changeFailureRate,
                failedDeploymentCount,
                dailyMetrics
        );
    }

    /**
     * Identifica deployments que causaron incidentes.
     */
    private Set<Long> identifyFailedDeployments(List<Deployment> deployments, List<Incident> allIncidents) {
        Set<Long> failedDeploymentIds = new HashSet<>();

        for (Deployment deployment : deployments) {
            LocalDateTime deploymentTime = deployment.getCreatedAt();
            LocalDateTime windowEnd = deploymentTime.plusHours(INCIDENT_CORRELATION_WINDOW_HOURS);

            boolean hasIncident = allIncidents.stream()
                    .anyMatch(incident -> {
                        boolean withinTimeWindow = !incident.getStartTime().isBefore(deploymentTime)
                                && incident.getStartTime().isBefore(windowEnd);

                        if (deployment.getServiceName() != null && incident.getServiceName() != null) {
                            return withinTimeWindow
                                    && deployment.getServiceName().equals(incident.getServiceName());
                        }

                        return withinTimeWindow
                                && deployment.getRepository().getId().equals(incident.getRepository().getId());
                    });

            if (hasIncident) {
                failedDeploymentIds.add(deployment.getId());
            }
        }

        return failedDeploymentIds;
    }

    /**
     * Calcula series de tiempo diarias.
     */
    private List<DailyMetricDto> calculateDailyTimeSeries(List<ChangeLeadTime> leadTimes,
                                                           Set<Long> failedDeploymentIds) {
        Map<LocalDate, List<ChangeLeadTime>> leadTimesByDate = leadTimes.stream()
                .collect(Collectors.groupingBy(lt ->
                        lt.getDeployment().getCreatedAt().toLocalDate()));

        return leadTimesByDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<ChangeLeadTime> dailyLeadTimes = entry.getValue();

                    double avgLeadTimeHours = dailyLeadTimes.stream()
                            .mapToDouble(lt -> lt.getLeadTimeInSeconds() / 3600.0)
                            .average()
                            .orElse(0.0);

                    Set<Long> dailyDeploymentIds = dailyLeadTimes.stream()
                            .map(lt -> lt.getDeployment().getId())
                            .collect(Collectors.toSet());
                    long deploymentCount = dailyDeploymentIds.size();
                    long commitCount = dailyLeadTimes.size();
                    long failedCount = dailyDeploymentIds.stream()
                            .filter(failedDeploymentIds::contains)
                            .count();

                    return new DailyMetricDto(
                            date,
                            avgLeadTimeHours,
                            deploymentCount,
                            commitCount,
                            failedCount
                    );
                })
                .sorted(Comparator.comparing(DailyMetricDto::date))
                .collect(Collectors.toList());
    }

    /**
     * Filtra commits basándose en deployments.
     */
    private List<Commit> filterCommitsByDeployments(List<Commit> commits,
                                                     LocalDate startDate,
                                                     LocalDate endDate,
                                                     List<Long> repositoryIds) {
        if (startDate == null && endDate == null && (repositoryIds == null || repositoryIds.isEmpty())) {
            return commits;
        }

        Set<String> commitShas = commits.stream()
                .map(Commit::getSha)
                .collect(Collectors.toSet());

        Set<String> filteredCommitShas = changeLeadTimeRepository.findAll().stream()
                .filter(lt -> commitShas.contains(lt.getCommit().getSha()))
                .filter(lt -> applyDeploymentDateFilter(lt.getDeployment(), startDate, endDate))
                .filter(lt -> applyRepositoryFilter(lt.getDeployment(), repositoryIds))
                .map(lt -> lt.getCommit().getSha())
                .collect(Collectors.toSet());

        return commits.stream()
                .filter(commit -> filteredCommitShas.contains(commit.getSha()))
                .collect(Collectors.toList());
    }

    /**
     * Aplica filtro de fecha sobre el deployment.
     */
    private boolean applyDeploymentDateFilter(Deployment deployment, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return true;
        }

        LocalDate deploymentDate = deployment.getCreatedAt().toLocalDate();

        if (startDate != null && deploymentDate.isBefore(startDate)) {
            return false;
        }

        if (endDate != null && deploymentDate.isAfter(endDate)) {
            return false;
        }

        return true;
    }

    /**
     * Aplica filtro de repositorios sobre el deployment.
     */
    private boolean applyRepositoryFilter(Deployment deployment, List<Long> repositoryIds) {
        if (repositoryIds == null || repositoryIds.isEmpty()) {
            return true;
        }

        return repositoryIds.contains(deployment.getRepository().getId());
    }

    /**
     * Crea una respuesta vacía cuando no hay datos.
     */
    private TechLeadMetricsResponse createEmptyMetricsResponse(String techLeadUsername, Team team) {
        return new TechLeadMetricsResponse(
                techLeadUsername,
                team.getId(),
                team.getName(),
                Collections.emptyList(),
                Collections.emptyList(),
                new CommitStatsDto(0L, 0L, null, null),
                new PullRequestStatsDto(0L, 0L, 0L),
                new DeveloperDoraMetricsDto(
                        null, null, null,
                        0L, 0L,
                        null, 0L,
                        Collections.emptyList()
                )
        );
    }
}
