package org.grubhart.pucp.tesis.module_domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String datadogIncidentId;

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
     * The Datadog service names this incident is associated with.
     * An incident can affect multiple services.
     */
    @ElementCollection
    @CollectionTable(name = "incident_services",
        joinColumns = @JoinColumn(name = "incident_id"))
    @Column(name = "service_name")
    private Set<String> serviceNames = new HashSet<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Incident() {
        // JPA constructor
    }

    public Incident(String datadogIncidentId, String title, IncidentState state,
                   IncidentSeverity severity, LocalDateTime startTime, LocalDateTime resolvedTime,
                   Long durationSeconds, Set<String> serviceNames, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.datadogIncidentId = datadogIncidentId;
        this.title = title;
        this.state = state;
        this.severity = severity;
        this.startTime = startTime;
        this.resolvedTime = resolvedTime;
        this.durationSeconds = durationSeconds;
        this.serviceNames = serviceNames != null ? serviceNames : new HashSet<>();
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

    public Set<String> getServiceNames() {
        return serviceNames;
    }

    public void setServiceNames(Set<String> serviceNames) {
        this.serviceNames = serviceNames;
    }

    public void addServiceName(String serviceName) {
        this.serviceNames.add(serviceName);
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
