package org.grubhart.pucp.tesis.module_processor;

import java.time.LocalDate;

public class DeploymentFrequency {

    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final int count;

    public DeploymentFrequency(LocalDate periodStart, LocalDate periodEnd, int count) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.count = count;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public int getCount() {
        return count;
    }
}
