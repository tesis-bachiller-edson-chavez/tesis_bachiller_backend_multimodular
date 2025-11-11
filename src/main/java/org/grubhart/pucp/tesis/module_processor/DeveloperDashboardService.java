package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.ChangeLeadTime;
import org.grubhart.pucp.tesis.module_domain.ChangeLeadTimeRepository;
import org.grubhart.pucp.tesis.module_domain.Commit;
import org.grubhart.pucp.tesis.module_domain.CommitRepository;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    private final CommitRepository commitRepository;
    private final ChangeLeadTimeRepository changeLeadTimeRepository;

    public DeveloperDashboardService(CommitRepository commitRepository,
                                     ChangeLeadTimeRepository changeLeadTimeRepository) {
        this.commitRepository = commitRepository;
        this.changeLeadTimeRepository = changeLeadTimeRepository;
    }

    /**
     * Obtiene las métricas completas del dashboard para un developer específico.
     * Solo incluye datos de repositorios donde el developer ha realizado commits.
     *
     * @param githubUsername El nombre de usuario de GitHub del developer
     * @return DeveloperMetricsResponse con todas las métricas agregadas
     */
    public DeveloperMetricsResponse getDeveloperMetrics(String githubUsername) {
        logger.info("Obteniendo métricas para el developer: {}", githubUsername);

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
        CommitStatsDto commitStats = calculateCommitStats(developerCommits, commitsByRepository.size());

        // TODO: Implementar estadísticas de Pull Requests cuando se agregue el campo 'author' a PullRequest
        PullRequestStatsDto pullRequestStats = new PullRequestStatsDto(0L, 0L, 0L);

        // Calcular métricas DORA
        DeveloperDoraMetricsDto doraMetrics = calculateDoraMetrics(developerCommits);

        logger.info("Métricas calculadas exitosamente para el developer: {}. Total commits: {}, Repositorios: {}, Lead Time promedio: {} horas",
                githubUsername, commitStats.totalCommits(), repositoryStats.size(), doraMetrics.averageLeadTimeHours());

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
     * Calcula métricas DORA para el developer.
     * Solo incluye Lead Time y Deployment Frequency ya que MTTR y CFR son métricas de servicio/equipo.
     */
    private DeveloperDoraMetricsDto calculateDoraMetrics(List<Commit> developerCommits) {
        if (developerCommits.isEmpty()) {
            return new DeveloperDoraMetricsDto(null, 0L, 0L, null, null);
        }

        // Obtener todos los ChangeLeadTime para los commits del developer
        Set<String> commitShas = developerCommits.stream()
                .map(Commit::getSha)
                .collect(Collectors.toSet());

        List<ChangeLeadTime> leadTimes = changeLeadTimeRepository.findAll().stream()
                .filter(lt -> commitShas.contains(lt.getCommit().getSha()))
                .collect(Collectors.toList());

        if (leadTimes.isEmpty()) {
            // No hay deployments con lead time calculado aún
            return new DeveloperDoraMetricsDto(null, 0L, 0L, null, null);
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
        long uniqueDeployments = leadTimes.stream()
                .map(lt -> lt.getDeployment().getId())
                .distinct()
                .count();

        return new DeveloperDoraMetricsDto(
                averageLeadTimeHours,
                uniqueDeployments,
                deploymentCommitCount,
                minLeadTimeHours,
                maxLeadTimeHours
        );
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
                new DeveloperDoraMetricsDto(null, 0L, 0L, null, null)
        );
    }
}
