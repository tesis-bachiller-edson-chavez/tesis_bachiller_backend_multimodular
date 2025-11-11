package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.DeploymentRepository;
import org.grubhart.pucp.tesis.module_domain.IncidentRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for calculating Change Failure Rate (CFR) metrics.
 *
 * CFR measures what percentage of changes to production result in degraded service.
 * Formula: CFR = Σ(incidents) / Σ(deployments)
 *
 * According to Rüegger et al. (2024), it's practically impossible to correlate
 * a specific incident with the exact deployment that caused it. Therefore, CFR is
 * calculated as the ratio between incidents and deployments in a given period,
 * not as direct correlation.
 */
@Service
public class CFRCalculationService {

    private final DeploymentRepository deploymentRepository;
    private final IncidentRepository incidentRepository;

    public CFRCalculationService(DeploymentRepository deploymentRepository, IncidentRepository incidentRepository) {
        this.deploymentRepository = deploymentRepository;
        this.incidentRepository = incidentRepository;
    }

    /**
     * Calculates CFR for a service across the specified period, broken down by period type.
     *
     * @param serviceName The Datadog service name
     * @param environment The deployment environment (typically "production")
     * @param rangeStart Start date of the analysis period
     * @param rangeEnd End date of the analysis period
     * @param periodType Period granularity (WEEKLY, BIWEEKLY, MONTHLY)
     * @return List of CFR metrics, one per period
     */
    public List<CFRMetric> calculate(String serviceName, String environment, LocalDate rangeStart, LocalDate rangeEnd, PeriodType periodType) {

        List<CFRMetric> results = new ArrayList<>();

        if (periodType == PeriodType.MONTHLY) {
            LocalDate currentMonthStart = rangeStart.with(TemporalAdjusters.firstDayOfMonth());

            while (!currentMonthStart.isAfter(rangeEnd)) {
                LocalDate currentMonthEnd = currentMonthStart.with(TemporalAdjusters.lastDayOfMonth());

                // Ensure we don't exceed the requested end range
                if (currentMonthEnd.isAfter(rangeEnd)) {
                    currentMonthEnd = rangeEnd;
                }

                CFRMetric metric = calculateForPeriod(serviceName, environment, currentMonthStart, currentMonthEnd);
                results.add(metric);

                // Move to next month
                currentMonthStart = currentMonthStart.plusMonths(1);
            }
        } else if (periodType == PeriodType.WEEKLY) {
            LocalDate currentWeekStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            while (!currentWeekStart.isAfter(rangeEnd)) {
                LocalDate currentWeekEnd = currentWeekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

                // Ensure we don't exceed the requested end range
                if (currentWeekEnd.isAfter(rangeEnd)) {
                    currentWeekEnd = rangeEnd;
                }

                CFRMetric metric = calculateForPeriod(serviceName, environment, currentWeekStart, currentWeekEnd);
                results.add(metric);

                // Move to next week
                currentWeekStart = currentWeekStart.plusWeeks(1);
            }
        } else if (periodType == PeriodType.BIWEEKLY) {
            LocalDate currentBiweeklyStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            while (!currentBiweeklyStart.isAfter(rangeEnd)) {
                LocalDate currentBiweeklyEnd = currentBiweeklyStart.plusWeeks(2).minusDays(1);

                // Ensure we don't exceed the requested end range
                if (currentBiweeklyEnd.isAfter(rangeEnd)) {
                    currentBiweeklyEnd = rangeEnd;
                }

                CFRMetric metric = calculateForPeriod(serviceName, environment, currentBiweeklyStart, currentBiweeklyEnd);
                results.add(metric);

                // Move to next biweekly period
                currentBiweeklyStart = currentBiweeklyStart.plusWeeks(2);
            }
        }

        return results;
    }

    /**
     * Calculates CFR for a specific period.
     *
     * @param serviceName The Datadog service name
     * @param environment The deployment environment
     * @param periodStart Start date of the period
     * @param periodEnd End date of the period
     * @return CFR metric for the period
     */
    private CFRMetric calculateForPeriod(String serviceName, String environment, LocalDate periodStart, LocalDate periodEnd) {
        // Count deployments to production in the period
        long deploymentCount = deploymentRepository.countByEnvironmentAndCreatedAtBetween(
                environment,
                periodStart,
                periodEnd
        );

        // Count incidents in the same period
        long incidentCount = incidentRepository.countByServiceNameAndStartTimeBetween(
                serviceName,
                periodStart.atStartOfDay(),
                periodEnd.atTime(23, 59, 59)
        );

        // Calculate rate (0 if no deployments)
        double rate = deploymentCount > 0 ? (double) incidentCount / deploymentCount : 0.0;

        return new CFRMetric(periodStart, periodEnd, deploymentCount, incidentCount, rate);
    }
}
