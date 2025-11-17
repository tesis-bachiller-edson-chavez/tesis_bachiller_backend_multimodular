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
 * Servicio para obtener métricas del dashboard específicas para el rol Engineering Manager.
 * Este servicio agrega métricas de múltiples equipos de la organización.
 */
@Service
public class EngineeringManagerDashboardService {

    private static final Logger logger = LoggerFactory.getLogger(EngineeringManagerDashboardService.class);
    private static final long INCIDENT_CORRELATION_WINDOW_HOURS = 48;

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final CommitRepository commitRepository;
    private final ChangeLeadTimeRepository changeLeadTimeRepository;
    private final IncidentRepository incidentRepository;
    private final PullRequestRepository pullRequestRepository;
    private final CommitParentRepository commitParentRepository;

    public EngineeringManagerDashboardService(UserRepository userRepository,
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
     * Obtiene las métricas completas del dashboard para un engineering manager.
     * Incluye métricas agregadas de todos los equipos (o equipos filtrados).
     *
     * @param engineeringManagerGithubUsername El nombre de usuario de GitHub del engineering manager
     * @param startDate Fecha de inicio del rango (basado en deployment.createdAt), opcional
     * @param endDate Fecha de fin del rango (basado en deployment.createdAt), opcional
     * @param repositoryIds Lista de IDs de repositorios para filtrar, opcional
     * @param teamIds Lista de IDs de equipos para filtrar, opcional (null = todos los equipos)
     * @param memberIds Lista de IDs de miembros para filtrar, opcional (null = todos los miembros de los equipos seleccionados)
     * @return EngineeringManagerMetricsResponse con todas las métricas agregadas
     */
    public EngineeringManagerMetricsResponse getEngineeringManagerMetrics(String engineeringManagerGithubUsername,
                                                                           LocalDate startDate,
                                                                           LocalDate endDate,
                                                                           List<Long> repositoryIds,
                                                                           List<Long> teamIds,
                                                                           List<Long> memberIds) {
        logger.info("Obteniendo métricas para el engineering manager: {} (startDate: {}, endDate: {}, " +
                        "repositoryIds: {}, teamIds: {}, memberIds: {})",
                engineeringManagerGithubUsername, startDate, endDate, repositoryIds, teamIds, memberIds);

        // Obtener el usuario engineering manager
        User engineeringManager = userRepository.findByGithubUsernameIgnoreCase(engineeringManagerGithubUsername)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Engineering manager no encontrado: " + engineeringManagerGithubUsername));

        // Obtener todos los equipos o equipos filtrados
        List<Team> teams = getFilteredTeams(teamIds);

        if (teams.isEmpty()) {
            logger.warn("No se encontraron equipos para el engineering manager");
            return createEmptyMetricsResponse(engineeringManagerGithubUsername);
        }

        logger.debug("Se encontraron {} equipos", teams.size());

        // Obtener todos los miembros de los equipos seleccionados
        List<User> allMembers = teams.stream()
                .flatMap(team -> userRepository.findByTeamId(team.getId()).stream())
                .distinct()
                .collect(Collectors.toList());

        if (allMembers.isEmpty()) {
            logger.warn("No se encontraron miembros en los equipos seleccionados");
            return createEmptyMetricsResponse(engineeringManagerGithubUsername);
        }

        // Filtrar miembros según memberIds si se especifica
        List<User> filteredMembers = filterAndValidateMembers(allMembers, memberIds, teams);

        if (filteredMembers.isEmpty()) {
            logger.warn("No se encontraron miembros después de aplicar el filtro memberIds: {}", memberIds);
            return createEmptyMetricsResponse(engineeringManagerGithubUsername);
        }

        logger.debug("Total de miembros: {} (filtrados: {})", allMembers.size(), filteredMembers.size());

        // Obtener commits de todos los miembros filtrados
        List<Commit> allCommits = getCommitsForMembers(filteredMembers);

        if (allCommits.isEmpty()) {
            logger.warn("No se encontraron commits para los miembros");
            return createEmptyMetricsResponse(engineeringManagerGithubUsername);
        }

        // Filtrar commits basándose en deployments (fecha y repositorio)
        List<Commit> filteredCommits = filterCommitsByDeployments(allCommits, startDate, endDate, repositoryIds);

        logger.debug("Después de aplicar filtros: {} commits (de {} totales)",
                filteredCommits.size(), allCommits.size());

        // Calcular estadísticas por equipo
        List<TeamMetricsDto> teamMetrics = calculateTeamMetrics(teams, filteredMembers, filteredCommits,
                startDate, endDate, repositoryIds);

        // Agrupar commits filtrados por repositorio
        Map<RepositoryConfig, List<Commit>> commitsByRepository = filteredCommits.stream()
                .collect(Collectors.groupingBy(Commit::getRepository));

        // Crear estadísticas agregadas por repositorio
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
        CommitStatsDto aggregatedCommitStats = calculateCommitStats(filteredCommits, commitsByRepository.size());

        // Calcular estadísticas agregadas de Pull Requests
        PullRequestStatsDto aggregatedPullRequestStats = calculatePullRequestStats(filteredCommits);

        // Calcular métricas DORA agregadas
        TeamDoraMetricsDto aggregatedDoraMetrics = calculateDoraMetrics(filteredCommits, startDate, endDate, repositoryIds);

        logger.info("Métricas calculadas exitosamente para el engineering manager: {}. Equipos: {}, " +
                        "Desarrolladores: {}, Total commits: {}, Repositorios: {}, PRs: {}, Lead Time promedio: {} horas",
                engineeringManagerGithubUsername, teams.size(), filteredMembers.size(),
                aggregatedCommitStats.totalCommits(), repositoryStats.size(),
                aggregatedPullRequestStats.totalPullRequests(), aggregatedDoraMetrics.averageLeadTimeHours());

        return new EngineeringManagerMetricsResponse(
                engineeringManagerGithubUsername,
                teams.size(),
                filteredMembers.size(),
                teamMetrics,
                repositoryStats,
                aggregatedCommitStats,
                aggregatedPullRequestStats,
                aggregatedDoraMetrics
        );
    }

    /**
     * Obtiene los equipos filtrados según teamIds.
     * Si teamIds es null o vacío, retorna todos los equipos.
     */
    private List<Team> getFilteredTeams(List<Long> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) {
            return teamRepository.findAll();
        }

        Set<Long> teamIdSet = new HashSet<>(teamIds);
        return teamRepository.findAll().stream()
                .filter(team -> teamIdSet.contains(team.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Filtra y valida que los miembros especificados pertenezcan a los equipos seleccionados.
     * Si memberIds es null o vacío, retorna todos los miembros.
     * Si memberIds contiene IDs que no pertenecen a ningún equipo seleccionado, lanza excepción.
     */
    private List<User> filterAndValidateMembers(List<User> allMembers, List<Long> memberIds, List<Team> teams) {
        if (memberIds == null || memberIds.isEmpty()) {
            return allMembers;
        }

        Set<Long> allMemberIds = allMembers.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        // Validar que todos los memberIds especificados pertenezcan a los equipos seleccionados
        List<Long> invalidMemberIds = memberIds.stream()
                .filter(id -> !allMemberIds.contains(id))
                .collect(Collectors.toList());

        if (!invalidMemberIds.isEmpty()) {
            Set<Long> teamIdSet = teams.stream().map(Team::getId).collect(Collectors.toSet());
            throw new IllegalArgumentException(
                    "Los siguientes memberIds no pertenecen a los equipos seleccionados " + teamIdSet + ": " + invalidMemberIds);
        }

        Set<Long> memberIdSet = new HashSet<>(memberIds);
        return allMembers.stream()
                .filter(member -> memberIdSet.contains(member.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los commits de una lista de usuarios.
     * Filtra commits de merge que no representan trabajo real.
     */
    private List<Commit> getCommitsForMembers(List<User> members) {
        Set<String> memberUsernames = members.stream()
                .map(User::getGithubUsername)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<Commit> allCommits = commitRepository.findAll().stream()
                .filter(commit -> memberUsernames.contains(commit.getAuthor().toLowerCase()))
                .collect(Collectors.toList());

        // Filtrar commits de merge (no representan trabajo real)
        List<Commit> filteredCommits = filterOutMergeCommits(allCommits);

        logger.debug("Commits de miembros: {} totales, {} después de filtrar merge commits",
                allCommits.size(), filteredCommits.size());

        return filteredCommits;
    }

    /**
     * Calcula estadísticas individuales para cada equipo.
     */
    private List<TeamMetricsDto> calculateTeamMetrics(List<Team> teams,
                                                       List<User> allFilteredMembers,
                                                       List<Commit> allFilteredCommits,
                                                       LocalDate startDate,
                                                       LocalDate endDate,
                                                       List<Long> repositoryIds) {
        return teams.stream()
                .map(team -> {
                    // Obtener miembros del equipo
                    List<User> teamMembers = allFilteredMembers.stream()
                            .filter(member -> team.getId().equals(member.getTeamId()))
                            .collect(Collectors.toList());

                    if (teamMembers.isEmpty()) {
                        // Equipo sin miembros filtrados
                        return createEmptyTeamMetrics(team);
                    }

                    Set<String> teamMemberUsernames = teamMembers.stream()
                            .map(User::getGithubUsername)
                            .map(String::toLowerCase)
                            .collect(Collectors.toSet());

                    // Filtrar commits del equipo
                    List<Commit> teamCommits = allFilteredCommits.stream()
                            .filter(commit -> teamMemberUsernames.contains(commit.getAuthor().toLowerCase()))
                            .collect(Collectors.toList());

                    long totalCommits = teamCommits.size();

                    // Calcular PRs del equipo
                    PullRequestStatsDto teamPRStats = calculatePullRequestStats(teamCommits);

                    // Agrupar commits por repositorio
                    Map<RepositoryConfig, List<Commit>> teamCommitsByRepo = teamCommits.stream()
                            .collect(Collectors.groupingBy(Commit::getRepository));

                    List<RepositoryStatsDto> teamRepositories = teamCommitsByRepo.entrySet().stream()
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

                    // Calcular estadísticas de commits del equipo
                    CommitStatsDto teamCommitStats = calculateCommitStats(teamCommits, teamCommitsByRepo.size());

                    // Calcular métricas DORA del equipo
                    TeamDoraMetricsDto teamDoraMetrics = calculateDoraMetrics(teamCommits, startDate, endDate, repositoryIds);

                    return new TeamMetricsDto(
                            team.getId(),
                            team.getName(),
                            teamMembers.size(),
                            totalCommits,
                            teamPRStats.totalPullRequests(),
                            teamRepositories.size(),
                            teamCommitStats,
                            teamPRStats,
                            teamDoraMetrics,
                            teamRepositories
                    );
                })
                .sorted(Comparator.comparing(TeamMetricsDto::totalCommits).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Crea métricas vacías para un equipo.
     */
    private TeamMetricsDto createEmptyTeamMetrics(Team team) {
        return new TeamMetricsDto(
                team.getId(),
                team.getName(),
                0,
                0L,
                0L,
                0,
                new CommitStatsDto(0L, 0L, null, null),
                new PullRequestStatsDto(0L, 0L, 0L),
                new TeamDoraMetricsDto(
                        null, null, null,
                        0L, 0L,
                        null, 0L,
                        null, null, null, 0L,
                        Collections.emptyList()
                ),
                Collections.emptyList()
        );
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
     * Calcula métricas DORA agregadas.
     */
    private TeamDoraMetricsDto calculateDoraMetrics(List<Commit> commits,
                                                     LocalDate startDate,
                                                     LocalDate endDate,
                                                     List<Long> repositoryIds) {
        if (commits.isEmpty()) {
            return new TeamDoraMetricsDto(
                    null, null, null,
                    0L, 0L,
                    null, 0L,
                    null, null, null, 0L,
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
            return new TeamDoraMetricsDto(
                    null, null, null,
                    0L, 0L,
                    null, 0L,
                    null, null, null, 0L,
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

        // Calculate MTTR metrics
        List<Incident> resolvedIncidents = filterResolvedIncidents(allIncidents, deployments, startDate, endDate, repositoryIds);
        Double averageMTTRHours = null;
        Double minMTTRHours = null;
        Double maxMTTRHours = null;
        long totalResolvedIncidents = resolvedIncidents.size();

        if (!resolvedIncidents.isEmpty()) {
            DoubleSummaryStatistics mttrStats = resolvedIncidents.stream()
                    .filter(incident -> incident.getDurationSeconds() != null)
                    .mapToDouble(incident -> incident.getDurationSeconds() / 3600.0)
                    .summaryStatistics();

            if (mttrStats.getCount() > 0) {
                averageMTTRHours = mttrStats.getAverage();
                minMTTRHours = mttrStats.getMin();
                maxMTTRHours = mttrStats.getMax();
            }
        }

        List<TeamDailyMetricDto> dailyMetrics = calculateDailyTimeSeries(leadTimes, failedDeploymentIds, resolvedIncidents);

        return new TeamDoraMetricsDto(
                averageLeadTimeHours,
                minLeadTimeHours,
                maxLeadTimeHours,
                totalDeploymentCount,
                deploymentCommitCount,
                changeFailureRate,
                failedDeploymentCount,
                averageMTTRHours,
                minMTTRHours,
                maxMTTRHours,
                totalResolvedIncidents,
                dailyMetrics
        );
    }

    /**
     * Filtra incidentes resueltos relacionados con los deployments,
     * aplicando los mismos filtros que las otras métricas DORA.
     */
    private List<Incident> filterResolvedIncidents(List<Incident> allIncidents,
                                                    List<Deployment> deployments,
                                                    LocalDate startDate,
                                                    LocalDate endDate,
                                                    List<Long> repositoryIds) {
        // Obtener los IDs de repositorios relevantes
        Set<Long> relevantRepoIds = deployments.stream()
                .map(d -> d.getRepository().getId())
                .collect(Collectors.toSet());

        return allIncidents.stream()
                .filter(incident -> incident.getState() == IncidentState.RESOLVED)
                .filter(incident -> incident.getDurationSeconds() != null)
                .filter(incident -> {
                    // Aplicar filtro de fecha basado en startTime del incidente
                    LocalDate incidentDate = incident.getStartTime().toLocalDate();
                    if (startDate != null && incidentDate.isBefore(startDate)) {
                        return false;
                    }
                    if (endDate != null && incidentDate.isAfter(endDate)) {
                        return false;
                    }
                    return true;
                })
                .filter(incident -> {
                    // Aplicar filtro de repositorio
                    if (repositoryIds != null && !repositoryIds.isEmpty()) {
                        return incident.getRepository() != null
                                && repositoryIds.contains(incident.getRepository().getId());
                    }
                    // Si no hay filtro de repositorio, usar solo incidentes de repos relevantes
                    return incident.getRepository() != null
                            && relevantRepoIds.contains(incident.getRepository().getId());
                })
                .collect(Collectors.toList());
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
    private List<TeamDailyMetricDto> calculateDailyTimeSeries(List<ChangeLeadTime> leadTimes,
                                                               Set<Long> failedDeploymentIds,
                                                               List<Incident> resolvedIncidents) {
        Map<LocalDate, List<ChangeLeadTime>> leadTimesByDate = leadTimes.stream()
                .collect(Collectors.groupingBy(lt ->
                        lt.getDeployment().getCreatedAt().toLocalDate()));

        Map<LocalDate, List<Incident>> incidentsByDate = resolvedIncidents.stream()
                .collect(Collectors.groupingBy(incident ->
                        incident.getStartTime().toLocalDate()));

        // Combinar todas las fechas únicas de lead times e incidentes
        Set<LocalDate> allDates = new HashSet<>();
        allDates.addAll(leadTimesByDate.keySet());
        allDates.addAll(incidentsByDate.keySet());

        return allDates.stream()
                .map(date -> {
                    List<ChangeLeadTime> dailyLeadTimes = leadTimesByDate.getOrDefault(date, Collections.emptyList());
                    List<Incident> dailyIncidents = incidentsByDate.getOrDefault(date, Collections.emptyList());

                    Double avgLeadTimeHours = null;
                    long deploymentCount = 0L;
                    long commitCount = 0L;
                    long failedCount = 0L;

                    if (!dailyLeadTimes.isEmpty()) {
                        avgLeadTimeHours = dailyLeadTimes.stream()
                                .mapToDouble(lt -> lt.getLeadTimeInSeconds() / 3600.0)
                                .average()
                                .orElse(0.0);

                        Set<Long> dailyDeploymentIds = dailyLeadTimes.stream()
                                .map(lt -> lt.getDeployment().getId())
                                .collect(Collectors.toSet());
                        deploymentCount = dailyDeploymentIds.size();
                        commitCount = dailyLeadTimes.size();
                        failedCount = dailyDeploymentIds.stream()
                                .filter(failedDeploymentIds::contains)
                                .count();
                    }

                    Double avgMTTRHours = null;
                    long resolvedIncidentCount = dailyIncidents.size();

                    if (!dailyIncidents.isEmpty()) {
                        OptionalDouble mttrAvg = dailyIncidents.stream()
                                .filter(incident -> incident.getDurationSeconds() != null)
                                .mapToDouble(incident -> incident.getDurationSeconds() / 3600.0)
                                .average();
                        avgMTTRHours = mttrAvg.isPresent() ? mttrAvg.getAsDouble() : null;
                    }

                    return new TeamDailyMetricDto(
                            date,
                            avgLeadTimeHours,
                            deploymentCount,
                            commitCount,
                            failedCount,
                            avgMTTRHours,
                            resolvedIncidentCount
                    );
                })
                .sorted(Comparator.comparing(TeamDailyMetricDto::date))
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
    private EngineeringManagerMetricsResponse createEmptyMetricsResponse(String engineeringManagerUsername) {
        return new EngineeringManagerMetricsResponse(
                engineeringManagerUsername,
                0,
                0,
                Collections.emptyList(),
                Collections.emptyList(),
                new CommitStatsDto(0L, 0L, null, null),
                new PullRequestStatsDto(0L, 0L, 0L),
                new TeamDoraMetricsDto(
                        null, null, null,
                        0L, 0L,
                        null, 0L,
                        null, null, null, 0L,
                        Collections.emptyList()
                )
        );
    }

    /**
     * Filtra commits de merge que no representan trabajo real.
     * Los commits de merge se guardan en la BD para mantener el grafo de commits,
     * pero no deben contarse en las métricas.
     */
    private List<Commit> filterOutMergeCommits(List<Commit> commits) {
        return commits.stream()
                .filter(commit -> !isMergeCommit(commit))
                .collect(Collectors.toList());
    }

    /**
     * Determina si un commit es un merge commit basándose en:
     * 1. Número de parents: >= 2 parents indica merge de múltiples ramas
     * 2. Mensaje del commit: comienza con "Merge pull request" o "Merge branch"
     */
    private boolean isMergeCommit(Commit commit) {
        // Criterio 1: Commits con 2 o más parents son merge commits
        if (commit.getParents() != null && commit.getParents().size() >= 2) {
            return true;
        }

        // Criterio 2: Mensaje comienza con patrones típicos de merge
        if (commit.getMessage() != null && !commit.getMessage().isEmpty()) {
            String messageLower = commit.getMessage().toLowerCase();
            return messageLower.startsWith("merge pull request") ||
                    messageLower.startsWith("merge branch") ||
                    messageLower.startsWith("merge remote-tracking branch");
        }

        return false;
    }
}