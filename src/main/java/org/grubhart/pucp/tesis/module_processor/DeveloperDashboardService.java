package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.ChangeLeadTime;
import org.grubhart.pucp.tesis.module_domain.ChangeLeadTimeRepository;
import org.grubhart.pucp.tesis.module_domain.Commit;
import org.grubhart.pucp.tesis.module_domain.CommitRepository;
import org.grubhart.pucp.tesis.module_domain.Deployment;
import org.grubhart.pucp.tesis.module_domain.Incident;
import org.grubhart.pucp.tesis.module_domain.IncidentRepository;
import org.grubhart.pucp.tesis.module_domain.PullRequest;
import org.grubhart.pucp.tesis.module_domain.PullRequestRepository;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio para obtener métricas del dashboard específicas para el rol Developer.
 * Este servicio implementa la lógica de negocio para filtrar y agregar datos
 * de métricas basándose en el usuario autenticado.
 */
@Service
public class DeveloperDashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DeveloperDashboardService.class);
    private static final long INCIDENT_CORRELATION_WINDOW_HOURS = 48;

    private final CommitRepository commitRepository;
    private final ChangeLeadTimeRepository changeLeadTimeRepository;
    private final IncidentRepository incidentRepository;
    private final PullRequestRepository pullRequestRepository;

    public DeveloperDashboardService(CommitRepository commitRepository,
                                     ChangeLeadTimeRepository changeLeadTimeRepository,
                                     IncidentRepository incidentRepository,
                                     PullRequestRepository pullRequestRepository) {
        this.commitRepository = commitRepository;
        this.changeLeadTimeRepository = changeLeadTimeRepository;
        this.incidentRepository = incidentRepository;
        this.pullRequestRepository = pullRequestRepository;
    }

    /**
     * Obtiene las métricas completas del dashboard para un developer específico.
     * Solo incluye datos de repositorios donde el developer ha realizado commits.
     *
     * @param githubUsername El nombre de usuario de GitHub del developer
     * @param startDate Fecha de inicio del rango (basado en deployment.createdAt), opcional
     * @param endDate Fecha de fin del rango (basado en deployment.createdAt), opcional
     * @param repositoryIds Lista de IDs de repositorios para filtrar, opcional
     * @return DeveloperMetricsResponse con todas las métricas agregadas
     */
    public DeveloperMetricsResponse getDeveloperMetrics(String githubUsername, LocalDate startDate,
                                                        LocalDate endDate, List<Long> repositoryIds) {
        logger.info("Obteniendo métricas para el developer: {} (startDate: {}, endDate: {}, repositoryIds: {})",
                githubUsername, startDate, endDate, repositoryIds);

        // Obtener todos los commits del developer
        List<Commit> developerCommits = commitRepository.findAll().stream()
                .filter(commit -> githubUsername.equalsIgnoreCase(commit.getAuthor()))
                .collect(Collectors.toList());

        logger.debug("Se encontraron {} commits para el developer {}", developerCommits.size(), githubUsername);

        if (developerCommits.isEmpty()) {
            logger.warn("No se encontraron commits para el developer: {}", githubUsername);
            return createEmptyMetricsResponse(githubUsername);
        }

        // Agrupar commits por repositorio
        Map<RepositoryConfig, List<Commit>> commitsByRepository = developerCommits.stream()
                .collect(Collectors.groupingBy(Commit::getRepository));

        // Crear estadísticas por repositorio (aplicar filtro de repositoryIds si existe)
        List<RepositoryStatsDto> repositoryStats = commitsByRepository.entrySet().stream()
                .filter(entry -> repositoryIds == null || repositoryIds.isEmpty() || repositoryIds.contains(entry.getKey().getId()))
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
        CommitStatsDto commitStats = calculateCommitStats(developerCommits, commitsByRepository.size());

        // Calcular estadísticas de Pull Requests
        PullRequestStatsDto pullRequestStats = calculatePullRequestStats(developerCommits);

        // Calcular métricas DORA (aplicando filtros de fecha y repositorio)
        DeveloperDoraMetricsDto doraMetrics = calculateDoraMetrics(developerCommits, startDate, endDate, repositoryIds);

        logger.info("Métricas calculadas exitosamente para el developer: {}. Total commits: {}, Repositorios: {}, " +
                        "PRs: {} (Merged: {}, Open: {}), Lead Time promedio: {} horas, Deployments: {}, CFR: {}%, Failed Deployments: {}, Daily Metrics: {}",
                githubUsername, commitStats.totalCommits(), repositoryStats.size(),
                pullRequestStats.totalPullRequests(), pullRequestStats.mergedPullRequests(), pullRequestStats.openPullRequests(),
                doraMetrics.averageLeadTimeHours(), doraMetrics.totalDeploymentCount(),
                doraMetrics.changeFailureRate(), doraMetrics.failedDeploymentCount(),
                doraMetrics.dailyMetrics().size());

        return new DeveloperMetricsResponse(
                githubUsername,
                repositoryStats,
                commitStats,
                pullRequestStats,
                doraMetrics
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
     * Calcula estadísticas de Pull Requests del developer.
     * Utiliza el campo firstCommitSha para correlacionar PRs con commits del developer.
     */
    private PullRequestStatsDto calculatePullRequestStats(List<Commit> developerCommits) {
        if (developerCommits.isEmpty()) {
            return new PullRequestStatsDto(0L, 0L, 0L);
        }

        // Obtener todos los SHAs de commits del developer
        Set<String> commitShas = developerCommits.stream()
                .map(Commit::getSha)
                .collect(Collectors.toSet());

        // Obtener todos los PRs y filtrar por firstCommitSha
        List<PullRequest> developerPullRequests = pullRequestRepository.findAll().stream()
                .filter(pr -> pr.getFirstCommitSha() != null && commitShas.contains(pr.getFirstCommitSha()))
                .collect(Collectors.toList());

        long totalPullRequests = developerPullRequests.size();

        // Contar PRs mergeados: state = "closed" y mergedAt != null
        long mergedPullRequests = developerPullRequests.stream()
                .filter(pr -> "closed".equalsIgnoreCase(pr.getState()) && pr.getMergedAt() != null)
                .count();

        // Contar PRs abiertos: state = "open"
        long openPullRequests = developerPullRequests.stream()
                .filter(pr -> "open".equalsIgnoreCase(pr.getState()))
                .count();

        return new PullRequestStatsDto(
                totalPullRequests,
                mergedPullRequests,
                openPullRequests
        );
    }

    /**
     * Calcula métricas DORA para el developer.
     * Incluye Lead Time, Deployment Frequency, Change Failure Rate y series de tiempo diarias.
     *
     * @param developerCommits Los commits del developer
     * @param startDate Fecha de inicio para filtrar por deployment.createdAt, opcional
     * @param endDate Fecha de fin para filtrar por deployment.createdAt, opcional
     * @param repositoryIds Lista de IDs de repositorios para filtrar, opcional
     */
    private DeveloperDoraMetricsDto calculateDoraMetrics(List<Commit> developerCommits, LocalDate startDate,
                                                         LocalDate endDate, List<Long> repositoryIds) {
        if (developerCommits.isEmpty()) {
            return new DeveloperDoraMetricsDto(
                    null, null, null,
                    0L, 0L,
                    null, 0L,
                    Collections.emptyList()
            );
        }

        // Obtener todos los ChangeLeadTime para los commits del developer
        Set<String> commitShas = developerCommits.stream()
                .map(Commit::getSha)
                .collect(Collectors.toSet());

        List<ChangeLeadTime> leadTimes = changeLeadTimeRepository.findAll().stream()
                .filter(lt -> commitShas.contains(lt.getCommit().getSha()))
                .filter(lt -> applyDeploymentDateFilter(lt.getDeployment(), startDate, endDate))
                .filter(lt -> applyRepositoryFilter(lt.getDeployment(), repositoryIds))
                .collect(Collectors.toList());

        if (leadTimes.isEmpty()) {
            // No hay deployments con lead time calculado aún
            return new DeveloperDoraMetricsDto(
                    null, null, null,
                    0L, 0L,
                    null, 0L,
                    Collections.emptyList()
            );
        }

        // Calcular estadísticas de lead time (convertir de segundos a horas)
        DoubleSummaryStatistics leadTimeStats = leadTimes.stream()
                .mapToDouble(lt -> lt.getLeadTimeInSeconds() / 3600.0) // Convertir a horas
                .summaryStatistics();

        double averageLeadTimeHours = leadTimeStats.getAverage();
        double minLeadTimeHours = leadTimeStats.getMin();
        double maxLeadTimeHours = leadTimeStats.getMax();
        long deploymentCommitCount = leadTimes.size();

        // Contar deployments únicos
        Set<Long> uniqueDeploymentIds = leadTimes.stream()
                .map(lt -> lt.getDeployment().getId())
                .collect(Collectors.toSet());
        long totalDeploymentCount = uniqueDeploymentIds.size();

        // Obtener todos los deployments del developer
        List<Deployment> deployments = leadTimes.stream()
                .map(ChangeLeadTime::getDeployment)
                .distinct()
                .collect(Collectors.toList());

        // Calcular CFR: identificar deployments que causaron incidentes
        List<Incident> allIncidents = incidentRepository.findAll();
        Set<Long> failedDeploymentIds = identifyFailedDeployments(deployments, allIncidents);
        long failedDeploymentCount = failedDeploymentIds.size();

        Double changeFailureRate = totalDeploymentCount > 0
                ? (failedDeploymentCount * 100.0) / totalDeploymentCount
                : null;

        // Calcular series de tiempo diarias
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
     * Identifica deployments que causaron incidentes dentro de una ventana de 48 horas.
     */
    private Set<Long> identifyFailedDeployments(List<Deployment> deployments, List<Incident> allIncidents) {
        Set<Long> failedDeploymentIds = new HashSet<>();

        for (Deployment deployment : deployments) {
            LocalDateTime deploymentTime = deployment.getCreatedAt();
            LocalDateTime windowEnd = deploymentTime.plusHours(INCIDENT_CORRELATION_WINDOW_HOURS);

            // Buscar incidentes que comenzaron dentro de la ventana de 48 horas
            boolean hasIncident = allIncidents.stream()
                    .anyMatch(incident -> {
                        boolean withinTimeWindow = !incident.getStartTime().isBefore(deploymentTime)
                                && incident.getStartTime().isBefore(windowEnd);

                        // Correlacionar por serviceName si está disponible
                        if (deployment.getServiceName() != null && incident.getServiceName() != null) {
                            return withinTimeWindow
                                    && deployment.getServiceName().equals(incident.getServiceName());
                        }

                        // Fallback: correlacionar por repositorio
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
     * Calcula series de tiempo diarias agrupando métricas por fecha.
     */
    private List<DailyMetricDto> calculateDailyTimeSeries(List<ChangeLeadTime> leadTimes,
                                                           Set<Long> failedDeploymentIds) {
        // Agrupar por fecha (LocalDate del deployment)
        Map<LocalDate, List<ChangeLeadTime>> leadTimesByDate = leadTimes.stream()
                .collect(Collectors.groupingBy(lt ->
                        lt.getDeployment().getCreatedAt().toLocalDate()));

        logger.debug("Agrupando {} ChangeLeadTime records en {} días distintos",
                leadTimes.size(), leadTimesByDate.size());

        // Calcular métricas para cada día
        return leadTimesByDate.entrySet().stream()
                .map(entry -> {
                    LocalDate date = entry.getKey();
                    List<ChangeLeadTime> dailyLeadTimes = entry.getValue();

                    // Log detallado para debugging
                    if (!dailyLeadTimes.isEmpty()) {
                        ChangeLeadTime firstLt = dailyLeadTimes.get(0);
                        logger.debug("Fecha: {}, Commits: {}, Deployment ID: {}, Deployment createdAt: {}, Commit SHA: {}, Commit date: {}",
                                date, dailyLeadTimes.size(),
                                firstLt.getDeployment().getId(),
                                firstLt.getDeployment().getCreatedAt(),
                                firstLt.getCommit().getSha(),
                                firstLt.getCommit().getDate());
                    }

                    // Calcular promedio de lead time para el día
                    double avgLeadTimeHours = dailyLeadTimes.stream()
                            .mapToDouble(lt -> lt.getLeadTimeInSeconds() / 3600.0)
                            .average()
                            .orElse(0.0);

                    // Contar deployments únicos del día
                    Set<Long> dailyDeploymentIds = dailyLeadTimes.stream()
                            .map(lt -> lt.getDeployment().getId())
                            .collect(Collectors.toSet());
                    long deploymentCount = dailyDeploymentIds.size();

                    // Contar commits del día
                    long commitCount = dailyLeadTimes.size();

                    // Contar deployments fallidos del día
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
     * Aplica filtro de fecha sobre el deployment.
     * Retorna true si el deployment está dentro del rango de fechas o si no hay filtro de fecha.
     */
    private boolean applyDeploymentDateFilter(Deployment deployment, LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return true; // Sin filtro de fecha
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
     * Retorna true si el deployment pertenece a alguno de los repositorios especificados o si no hay filtro.
     */
    private boolean applyRepositoryFilter(Deployment deployment, List<Long> repositoryIds) {
        if (repositoryIds == null || repositoryIds.isEmpty()) {
            return true; // Sin filtro de repositorio
        }

        return repositoryIds.contains(deployment.getRepository().getId());
    }

    /**
     * Crea una respuesta vacía cuando no hay datos para el developer.
     */
    private DeveloperMetricsResponse createEmptyMetricsResponse(String githubUsername) {
        return new DeveloperMetricsResponse(
                githubUsername,
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
