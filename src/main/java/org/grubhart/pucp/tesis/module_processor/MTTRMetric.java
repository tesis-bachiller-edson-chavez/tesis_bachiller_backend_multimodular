package org.grubhart.pucp.tesis.module_processor;

import java.time.LocalDate;

/**
 * Represents the Mean Time To Recovery (MTTR) metric for a specific period.
 * MTTR measures how long it takes to recover from a failure/incident.
 *
 * Formula: MTTR = Σ(incident_duration) / Σ(incidents_resolved)
 */
public class MTTRMetric {

    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int incidentCount;
    private final long averageDurationSeconds;

    /**
     * Creates an MTTR metric for a period.
     *
     * @param periodStart Start date of the period
     * @param periodEnd End date of the period
     * @param incidentCount Number of resolved incidents in the period
     * @param averageDurationSeconds Average duration in seconds (0 if no incidents)
     */
    public MTTRMetric(LocalDate periodStart, LocalDate periodEnd, int incidentCount, long averageDurationSeconds) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.incidentCount = incidentCount;
        this.averageDurationSeconds = averageDurationSeconds;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public int getIncidentCount() {
        return incidentCount;
    }

    public long getAverageDurationSeconds() {
        return averageDurationSeconds;
    }

    /**
     * Returns average duration in minutes for convenience.
     */
    public double getAverageDurationMinutes() {
        return averageDurationSeconds / 60.0;
    }

    /**
     * Returns average duration in hours for convenience.
     */
    public double getAverageDurationHours() {
        return averageDurationSeconds / 3600.0;
    }
}
