package org.grubhart.pucp.tesis.module_collector.service;

import org.grubhart.pucp.tesis.module_domain.GithubRepositoryCollector;
import org.grubhart.pucp.tesis.module_domain.GithubRepositoryDto;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfigRepository;
import org.grubhart.pucp.tesis.module_domain.RepositorySyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio para sincronizar repositorios desde GitHub a la base de datos local.
 * Implementa sincronización idempotente: crea nuevos repositorios sin modificar los existentes.
 */
@Service
public class RepositorySyncService implements org.grubhart.pucp.tesis.module_domain.RepositorySyncService {

    private static final Logger logger = LoggerFactory.getLogger(RepositorySyncService.class);

    private final GithubRepositoryCollector githubRepositoryCollector;
    private final RepositoryConfigRepository repositoryConfigRepository;

    public RepositorySyncService(
            GithubRepositoryCollector githubRepositoryCollector,
            RepositoryConfigRepository repositoryConfigRepository) {
        this.githubRepositoryCollector = githubRepositoryCollector;
        this.repositoryConfigRepository = repositoryConfigRepository;
    }

    /**
     * Sincroniza repositorios desde GitHub a la base de datos de forma idempotente.
     * - Crea nuevos repositorios que no existen
     * - NO modifica repositorios existentes (especialmente datadogServiceName)
     * - NO elimina repositorios que ya no aparecen en GitHub
     *
     * @return Resultado de la sincronización con estadísticas
     */
    public RepositorySyncResult synchronizeRepositories() {
        logger.info("Starting repository synchronization from GitHub");

        // 1. Get remote repositories from GitHub
        List<GithubRepositoryDto> githubRepos = githubRepositoryCollector.getUserRepositories();
        logger.debug("Fetched {} repositories from GitHub", githubRepos.size());

        // Create a map for fast lookup by URL
        Map<String, GithubRepositoryDto> githubReposByUrl = githubRepos.stream()
                .collect(Collectors.toMap(GithubRepositoryDto::htmlUrl, Function.identity()));

        // 2. Get all local repositories
        List<RepositoryConfig> localRepos = repositoryConfigRepository.findAll();
        logger.debug("Found {} repositories in local database", localRepos.size());

        // Create a set of existing URLs for fast lookup
        Map<String, RepositoryConfig> localReposByUrl = localRepos.stream()
                .collect(Collectors.toMap(RepositoryConfig::getRepositoryUrl, Function.identity()));

        // 3. Identify new repositories to create
        List<RepositoryConfig> newReposToCreate = new ArrayList<>();
        int unchangedCount = 0;

        for (GithubRepositoryDto githubRepo : githubRepos) {
            String repoUrl = githubRepo.htmlUrl();

            if (!localReposByUrl.containsKey(repoUrl)) {
                // New repository, create it
                RepositoryConfig newRepo = new RepositoryConfig(repoUrl);
                // datadogServiceName is null by default
                newReposToCreate.add(newRepo);
                logger.debug("New repository to create: {}", repoUrl);
            } else {
                // Existing repository, don't modify it
                unchangedCount++;
                logger.debug("Repository already exists, skipping: {}", repoUrl);
            }
        }

        // 4. Save only new repositories (idempotent operation)
        if (!newReposToCreate.isEmpty()) {
            repositoryConfigRepository.saveAll(newReposToCreate);
            logger.info("Created {} new repositories", newReposToCreate.size());
        }

        // 5. Calculate final statistics
        int totalAfterSync = localRepos.size() + newReposToCreate.size();

        RepositorySyncResult result = new RepositorySyncResult(
                newReposToCreate.size(),
                totalAfterSync,
                unchangedCount
        );

        logger.info("Repository synchronization completed: {} new, {} unchanged, {} total",
                result.newRepositories(), result.unchanged(), result.totalRepositories());

        return result;
    }
}