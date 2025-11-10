package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.Incident;
import org.grubhart.pucp.tesis.module_domain.IncidentRepository;
import org.grubhart.pucp.tesis.module_domain.IncidentState;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for calculating Mean Time To Recovery (MTTR) metrics.
 *
 * MTTR measures how long it takes to recover from failures/incidents.
 * Formula: MTTR = Σ(incident_duration) / Σ(resolved_incidents)
 *
 * Only RESOLVED incidents are included in the calculation.
 */
@Service
public class MTTRCalculationService {

    private final IncidentRepository incidentRepository;

    public MTTRCalculationService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    /**
     * Calculates MTTR for a service across the specified period, broken down by period type.
     *
     * @param serviceName The Datadog service name
     * @param rangeStart Start date of the analysis period
     * @param rangeEnd End date of the analysis period
     * @param periodType Period granularity (WEEKLY, BIWEEKLY, MONTHLY)
     * @return List of MTTR metrics, one per period
     */
    public List<MTTRMetric> calculate(String serviceName, LocalDate rangeStart, LocalDate rangeEnd, PeriodType periodType) {

        List<MTTRMetric> results = new ArrayList<>();

        if (periodType == PeriodType.MONTHLY) {
            LocalDate currentMonthStart = rangeStart.with(TemporalAdjusters.firstDayOfMonth());

            while (!currentMonthStart.isAfter(rangeEnd)) {
                LocalDate currentMonthEnd = currentMonthStart.with(TemporalAdjusters.lastDayOfMonth());

                // Ensure we don't exceed the requested end range
                if (currentMonthEnd.isAfter(rangeEnd)) {
                    currentMonthEnd = rangeEnd;
                }

                MTTRMetric metric = calculateForPeriod(serviceName, currentMonthStart, currentMonthEnd);
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

                MTTRMetric metric = calculateForPeriod(serviceName, currentWeekStart, currentWeekEnd);
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

                MTTRMetric metric = calculateForPeriod(serviceName, currentBiweeklyStart, currentBiweeklyEnd);
                results.add(metric);

                // Move to next biweekly period
                currentBiweeklyStart = currentBiweeklyStart.plusWeeks(2);
            }
        }

        return results;
    }

    /**
     * Calculates MTTR for a specific period.
     *
     * @param serviceName The Datadog service name
     * @param periodStart Start date of the period
     * @param periodEnd End date of the period
     * @return MTTR metric for the period
     */
    private MTTRMetric calculateForPeriod(String serviceName, LocalDate periodStart, LocalDate periodEnd) {
        // Fetch only RESOLVED incidents in the period
        List<Incident> resolvedIncidents = incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                serviceName,
                IncidentState.RESOLVED,
                periodStart.atStartOfDay(),
                periodEnd.atTime(23, 59, 59)
        );

        if (resolvedIncidents.isEmpty()) {
            return new MTTRMetric(periodStart, periodEnd, 0, 0L);
        }

        // Calculate average duration
        long totalDurationSeconds = resolvedIncidents.stream()
                .mapToLong(incident -> incident.getDurationSeconds() != null ? incident.getDurationSeconds() : 0L)
                .sum();

        long averageDuration = totalDurationSeconds / resolvedIncidents.size();

        return new MTTRMetric(periodStart, periodEnd, resolvedIncidents.size(), averageDuration);
    }
}
