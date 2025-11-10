package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String datadogIncidentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryConfig repository;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentState state;

    @Enumerated(EnumType.STRING)
    private IncidentSeverity severity;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime resolvedTime;

    private Long durationSeconds;

    /**
     * The Datadog service name this incident is associated with.
     * This should match the DD_SERVICE tag in Datadog.
     * Maintained for reference and debugging purposes.
     */
    private String serviceName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Incident() {
        // JPA constructor
    }

    public Incident(String datadogIncidentId, RepositoryConfig repository, String title, IncidentState state,
                   IncidentSeverity severity, LocalDateTime startTime, LocalDateTime resolvedTime,
                   Long durationSeconds, String serviceName, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.datadogIncidentId = datadogIncidentId;
        this.repository = repository;
        this.title = title;
        this.state = state;
        this.severity = severity;
        this.startTime = startTime;
        this.resolvedTime = resolvedTime;
        this.durationSeconds = durationSeconds;
        this.serviceName = serviceName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDatadogIncidentId() {
        return datadogIncidentId;
    }

    public void setDatadogIncidentId(String datadogIncidentId) {
        this.datadogIncidentId = datadogIncidentId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public IncidentState getState() {
        return state;
    }

    public void setState(IncidentState state) {
        this.state = state;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IncidentSeverity severity) {
        this.severity = severity;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getResolvedTime() {
        return resolvedTime;
    }

    public void setResolvedTime(LocalDateTime resolvedTime) {
        this.resolvedTime = resolvedTime;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public RepositoryConfig getRepository() {
        return repository;
    }

    public void setRepository(RepositoryConfig repository) {
        this.repository = repository;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Incident incident = (Incident) o;
        return Objects.equals(id, incident.id) &&
               Objects.equals(datadogIncidentId, incident.datadogIncidentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, datadogIncidentId);
    }
}
