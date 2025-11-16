package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Entity
public class Commit {

    @Id
    private String sha;
    private String author;
    @Lob // Usamos @Lob para textos largos, que se mapea a TEXT o CLOB
    private String message;
    private LocalDateTime date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryConfig repository;

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinTable(
            name = "commit_parent",
            joinColumns = @JoinColumn(name = "commit_sha"),
            inverseJoinColumns = @JoinColumn(name = "parent_sha")
    )
    private List<Commit> parents;



    public Commit(String sha, String author, String message, LocalDateTime date, RepositoryConfig repository) {
        this.sha = sha;
        this.author = author;
        this.message = message;
        this.date = date;
        this.repository = repository;
    }

    /**
     * Constructor de conveniencia para crear una entidad Commit a partir de un GithubCommitDto.
     * Esto centraliza la lógica de mapeo y la elimina de los servicios.
     *
     * IMPORTANTE: Extrae el autor REAL del commit (quien escribió el código), no quien lo mergeo/revisó.
     *
     * @param dto El objeto de transferencia de datos de la API de GitHub.
     * @param repository El repositorio al que pertenece este commit.
     * @param userRepository Repositorio para buscar usuarios por email o ID de GitHub.
     */
    public Commit(GithubCommitDto dto, RepositoryConfig repository, UserRepository userRepository) {
        this.sha = dto.getSha();
        this.repository = repository;

        // Extraer el autor REAL del commit GIT (no el usuario asociado en GitHub que puede ser el merger)
        this.author = extractRealAuthor(dto, userRepository);

        this.message = Optional.ofNullable(dto.getCommit())
                .map(GithubCommitDto.Commit::getMessage)
                .orElse("");
        this.date = Optional.ofNullable(dto.getCommit())
                .map(GithubCommitDto.Commit::getAuthor)
                .map(GithubCommitDto.CommitAuthor::getDate)
                .map(d -> Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime())
                .orElse(LocalDateTime.now());
    }

    /**
     * Extrae el githubUsername del autor REAL del commit.
     * Prioriza el email del commit GIT sobre el usuario asociado en GitHub.
     *
     * Estrategia:
     * 1. Intentar con email del commit GIT (commit.author.email)
     * 2. Si es email noreply de GitHub, extraer username/id del formato
     * 3. Buscar en BD por email, github_id o github_username
     * 4. Fallback al nombre del autor o "N/A"
     */
    private String extractRealAuthor(GithubCommitDto dto, UserRepository userRepository) {
        // Obtener email del commit GIT
        String commitEmail = Optional.ofNullable(dto.getCommit())
                .map(GithubCommitDto.Commit::getAuthor)
                .map(GithubCommitDto.CommitAuthor::getEmail)
                .orElse(null);

        if (commitEmail != null && !commitEmail.isEmpty()) {
            // Caso 1: Email es noreply de GitHub
            // Formatos: username@users.noreply.github.com o github_id+username@users.noreply.github.com
            if (commitEmail.endsWith("@users.noreply.github.com")) {
                String localPart = commitEmail.substring(0, commitEmail.indexOf('@'));

                // Formato: github_id+username
                if (localPart.contains("+")) {
                    String[] parts = localPart.split("\\+", 2);
                    try {
                        Long githubId = Long.parseLong(parts[0]);
                        return userRepository.findByGithubId(githubId)
                                .map(User::getGithubUsername)
                                .orElse(parts[1]); // Usar el username del email como fallback
                    } catch (NumberFormatException e) {
                        // Si no es un número, usar el username del email
                        return parts[1];
                    }
                } else {
                    // Formato: username (sin github_id)
                    return userRepository.findByGithubUsernameIgnoreCase(localPart)
                            .map(User::getGithubUsername)
                            .orElse(localPart);
                }
            }

            // Caso 2: Email real (público)
            // Buscar usuario por email en la BD
            User user = userRepository.findByEmailIgnoreCase(commitEmail).orElse(null);
            if (user != null) {
                return user.getGithubUsername();
            }
        }

        // Caso 3: No hay email o no se pudo resolver
        // Usar el nombre del autor del commit como último recurso
        return Optional.ofNullable(dto.getCommit())
                .map(GithubCommitDto.Commit::getAuthor)
                .map(GithubCommitDto.CommitAuthor::getName)
                .orElse("N/A");
    }

    public Commit() {

    }

    // Getters and Setters

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    public List<Commit> getParents() {
        return parents;
    }

    public void setParents(List<Commit> parents) {
        this.parents = parents;
    }

    public RepositoryConfig getRepository() {
        return repository;
    }

    public void setRepository(RepositoryConfig repository) {
        this.repository = repository;
    }
}
