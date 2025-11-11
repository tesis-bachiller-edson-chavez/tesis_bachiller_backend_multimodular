package org.grubhart.pucp.tesis.module_processor;

import java.time.LocalDate;

/**
 * Represents the Change Failure Rate (CFR) metric for a specific period.
 * CFR measures what percentage of changes to production result in degraded service.
 *
 * Formula: CFR = Σ(incidents) / Σ(deployments)
 *
 * Note: According to Rüegger et al. (2024), it's practically impossible to correlate
 * a specific incident with the exact deployment that caused it. Therefore, CFR is
 * calculated as the ratio between incidents and deployments in a given period,
 * not as direct correlation.
 */
public class CFRMetric {

    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final long deploymentCount;
    private final long incidentCount;
    private final double rate;

    /**
     * Creates a CFR metric for a period.
     *
     * @param periodStart Start date of the period
     * @param periodEnd End date of the period
     * @param deploymentCount Number of deployments in the period
     * @param incidentCount Number of incidents in the period
     * @param rate CFR as a decimal (0.15 = 15%)
     */
    public CFRMetric(LocalDate periodStart, LocalDate periodEnd, long deploymentCount, long incidentCount, double rate) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.deploymentCount = deploymentCount;
        this.incidentCount = incidentCount;
        this.rate = rate;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public long getDeploymentCount() {
        return deploymentCount;
    }

    public long getIncidentCount() {
        return incidentCount;
    }

    /**
     * Returns CFR as a decimal (0.15 = 15%).
     */
    public double getRate() {
        return rate;
    }

    /**
     * Returns CFR as a percentage (15.0 = 15%).
     */
    public double getRatePercentage() {
        return rate * 100.0;
    }

    /**
     * Interprets the CFR according to DORA 2022 benchmarks.
     *
     * @return "Elite", "High", "Medium", or "Low"
     */
    public String getDoraLevel() {
        double percentage = getRatePercentage();
        if (percentage <= 15.0) {
            return "Elite";
        } else if (percentage <= 30.0) {
            return "High";
        } else if (percentage <= 45.0) {
            return "Medium";
        } else {
            return "Low";
        }
    }
}
