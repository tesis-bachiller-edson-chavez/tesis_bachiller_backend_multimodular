package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Entity
public class Commit {

    @Id
    private String sha;
    private String author;
    @Lob // Usamos @Lob para textos largos, que se mapea a TEXT o CLOB
    private String message;
    private LocalDateTime date;



    public Commit(String sha, String author, String message, LocalDateTime date) {
        this.sha = sha;
        this.author = author;
        this.message = message;
        this.date = date;
    }

    /**
     * Constructor de conveniencia para crear una entidad Commit a partir de un GithubCommitDto.
     * Esto centraliza la lógica de mapeo y la elimina de los servicios.
     * @param dto El objeto de transferencia de datos de la API de GitHub.
     */
    public Commit(GithubCommitDto dto) {
        this.sha = dto.getSha();
        // Priorizamos el 'login' del usuario de GitHub, que es más consistente.
        // Si no está, usamos el nombre del autor del commit como respaldo.
        this.author = Optional.ofNullable(dto.getAuthor())
                .map(GithubCommitDto.Author::getLogin)
                .or(() -> Optional.ofNullable(dto.getCommit())
                        .map(GithubCommitDto.Commit::getAuthor)
                        .map(GithubCommitDto.CommitAuthor::getName))
                .orElse("N/A");
        this.message = Optional.ofNullable(dto.getCommit())
                .map(GithubCommitDto.Commit::getMessage)
                .orElse("");
        this.date = Optional.ofNullable(dto.getCommit())
                .map(GithubCommitDto.Commit::getAuthor)
                .map(GithubCommitDto.CommitAuthor::getDate)
                .map(d -> Instant.ofEpochMilli(d.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime())
                .orElse(LocalDateTime.now());
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
}
