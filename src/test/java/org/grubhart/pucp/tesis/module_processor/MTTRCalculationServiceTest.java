package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.Incident;
import org.grubhart.pucp.tesis.module_domain.IncidentRepository;
import org.grubhart.pucp.tesis.module_domain.IncidentState;
import org.grubhart.pucp.tesis.module_domain.RepositoryConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MTTRCalculationServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private MTTRCalculationService mttrCalculationService;

    private static final String SERVICE_NAME = "tesis-backend";

    @Test
    void testCalculate_monthlyPeriod_returnsCorrectMTTR() {
        // GIVEN: A date range for a single month and a list of 3 resolved incidents within that month
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);
        PeriodType periodType = PeriodType.MONTHLY;

        RepositoryConfig repo = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);

        List<Incident> incidentsInNovember = List.of(
            createIncident("inc-1", repo, LocalDate.of(2025, 11, 5), 300L),  // 5 minutes
            createIncident("inc-2", repo, LocalDate.of(2025, 11, 15), 600L), // 10 minutes
            createIncident("inc-3", repo, LocalDate.of(2025, 11, 25), 900L)  // 15 minutes
        );

        when(incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                eq(rangeStart.atStartOfDay()),
                eq(rangeEnd.atTime(23, 59, 59))))
                .thenReturn(incidentsInNovember);

        // WHEN: the service calculates MTTR for that monthly period
        List<MTTRMetric> result = mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, periodType);

        // THEN: the result should be a list with a single metric object for that month
        assertEquals(1, result.size());
        MTTRMetric novemberMetric = result.get(0);
        assertEquals(3, novemberMetric.getIncidentCount());
        // Average: (300 + 600 + 900) / 3 = 600 seconds = 10 minutes
        assertEquals(600L, novemberMetric.getAverageDurationSeconds());
        assertEquals(10.0, novemberMetric.getAverageDurationMinutes(), 0.01);
        assertEquals(LocalDate.of(2025, 11, 1), novemberMetric.getPeriodStart());
        assertEquals(LocalDate.of(2025, 11, 30), novemberMetric.getPeriodEnd());
    }

    @Test
    void testCalculate_weeklyPeriod_returnsCorrectMTTR() {
        // GIVEN: A date range for a single week (Week 45 of 2025: Nov 3 to Nov 9)
        LocalDate rangeStart = LocalDate.of(2025, 11, 3);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 9);
        PeriodType periodType = PeriodType.WEEKLY;

        RepositoryConfig repo = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);

        List<Incident> incidentsInWeek45 = List.of(
            createIncident("inc-4", repo, LocalDate.of(2025, 11, 4), 1200L), // 20 minutes
            createIncident("inc-5", repo, LocalDate.of(2025, 11, 8), 1800L)  // 30 minutes
        );

        // The service will calculate the boundaries of the week and query for it
        LocalDate expectedWeekStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate expectedWeekEnd = rangeEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        when(incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                eq(expectedWeekStart.atStartOfDay()),
                eq(expectedWeekEnd.atTime(23, 59, 59))))
                .thenReturn(incidentsInWeek45);

        // WHEN
        List<MTTRMetric> result = mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, periodType);

        // THEN: The result should be a single metric for that week
        assertEquals(1, result.size());
        MTTRMetric week45Metric = result.get(0);
        assertEquals(2, week45Metric.getIncidentCount());
        // Average: (1200 + 1800) / 2 = 1500 seconds = 25 minutes
        assertEquals(1500L, week45Metric.getAverageDurationSeconds());
        assertEquals(25.0, week45Metric.getAverageDurationMinutes(), 0.01);
        assertEquals(expectedWeekStart, week45Metric.getPeriodStart());
        assertEquals(expectedWeekEnd, week45Metric.getPeriodEnd());
    }

    @Test
    void testCalculate_biweeklyPeriod_returnsCorrectMTTR() {
        // GIVEN: A date range for a bi-weekly period (Nov 3 to Nov 16, 2025)
        LocalDate rangeStart = LocalDate.of(2025, 11, 3);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 16);
        PeriodType periodType = PeriodType.BIWEEKLY;

        RepositoryConfig repo = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);

        List<Incident> incidentsInBiweek = List.of(
            createIncident("inc-6", repo, LocalDate.of(2025, 11, 4), 600L),   // 10 min
            createIncident("inc-7", repo, LocalDate.of(2025, 11, 8), 1200L),  // 20 min
            createIncident("inc-8", repo, LocalDate.of(2025, 11, 11), 1800L), // 30 min
            createIncident("inc-9", repo, LocalDate.of(2025, 11, 15), 2400L)  // 40 min
        );

        // The service will calculate the boundaries of the bi-weekly period
        LocalDate expectedBiweeklyStart = LocalDate.of(2025, 11, 3);
        LocalDate expectedBiweeklyEnd = LocalDate.of(2025, 11, 16);

        when(incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                eq(expectedBiweeklyStart.atStartOfDay()),
                eq(expectedBiweeklyEnd.atTime(23, 59, 59))))
                .thenReturn(incidentsInBiweek);

        // WHEN
        List<MTTRMetric> result = mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, periodType);

        // THEN: The result should be a single metric for that bi-weekly period
        assertEquals(1, result.size());
        MTTRMetric biweeklyMetric = result.get(0);
        assertEquals(4, biweeklyMetric.getIncidentCount());
        // Average: (600 + 1200 + 1800 + 2400) / 4 = 1500 seconds = 25 minutes
        assertEquals(1500L, biweeklyMetric.getAverageDurationSeconds());
        assertEquals(25.0, biweeklyMetric.getAverageDurationMinutes(), 0.01);
        assertEquals(expectedBiweeklyStart, biweeklyMetric.getPeriodStart());
        assertEquals(expectedBiweeklyEnd, biweeklyMetric.getPeriodEnd());
    }

    @Test
    void testCalculate_noResolvedIncidents_returnsZeroMTTR() {
        // GIVEN: A date range with no resolved incidents
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);
        PeriodType periodType = PeriodType.MONTHLY;

        when(incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        // WHEN
        List<MTTRMetric> result = mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, periodType);

        // THEN: The result should have zero count and zero average duration
        assertEquals(1, result.size());
        MTTRMetric metric = result.get(0);
        assertEquals(0, metric.getIncidentCount());
        assertEquals(0L, metric.getAverageDurationSeconds());
        assertEquals(0.0, metric.getAverageDurationMinutes(), 0.01);
    }

    @Test
    void testCalculate_monthlyPeriodWithPartialEndRange_usesCorrectEndDate() {
        // GIVEN: A date range that ends before the end of the month
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 15); // Partial range
        PeriodType periodType = PeriodType.MONTHLY;

        LocalDate monthStart = LocalDate.of(2025, 11, 1);

        // The service should query from the start of the month to the specified (partial) end date
        when(incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                eq(monthStart.atStartOfDay()),
                eq(rangeEnd.atTime(23, 59, 59))))
                .thenReturn(Collections.emptyList());

        // WHEN
        List<MTTRMetric> result = mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, periodType);

        // THEN: The result should have a periodEnd matching the partial rangeEnd
        assertEquals(1, result.size());
        MTTRMetric partialMonthMetric = result.get(0);
        assertEquals(0, partialMonthMetric.getIncidentCount());
        assertEquals(monthStart, partialMonthMetric.getPeriodStart());
        assertEquals(rangeEnd, partialMonthMetric.getPeriodEnd()); // Verify the range is correctly clipped
    }

    @Test
    void testCalculate_weeklyPeriodWithPartialEndRange_usesCorrectEndDate() {
        // GIVEN: A date range that ends before the end of the week
        LocalDate rangeStart = LocalDate.of(2025, 11, 3); // Monday
        LocalDate rangeEnd = LocalDate.of(2025, 11, 5);   // Wednesday
        PeriodType periodType = PeriodType.WEEKLY;

        LocalDate weekStart = LocalDate.of(2025, 11, 3);

        when(incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                eq(weekStart.atStartOfDay()),
                eq(rangeEnd.atTime(23, 59, 59))))
                .thenReturn(Collections.emptyList());

        // WHEN
        List<MTTRMetric> result = mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, periodType);

        // THEN: The result should have a periodEnd matching the partial rangeEnd
        assertEquals(1, result.size());
        MTTRMetric partialWeekMetric = result.get(0);
        assertEquals(0, partialWeekMetric.getIncidentCount());
        assertEquals(weekStart, partialWeekMetric.getPeriodStart());
        assertEquals(rangeEnd, partialWeekMetric.getPeriodEnd()); // Verify the range is correctly clipped
    }

    @Test
    void testCalculate_biweeklyPeriodWithPartialEndRange_usesCorrectEndDate() {
        // GIVEN: A date range that ends before the end of the bi-weekly period
        LocalDate rangeStart = LocalDate.of(2025, 11, 3); // Bi-week starts on Monday
        LocalDate rangeEnd = LocalDate.of(2025, 11, 10);  // Partial range ends on the next Monday
        PeriodType periodType = PeriodType.BIWEEKLY;

        LocalDate biweekStart = LocalDate.of(2025, 11, 3);

        when(incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                eq(biweekStart.atStartOfDay()),
                eq(rangeEnd.atTime(23, 59, 59))))
                .thenReturn(Collections.emptyList());

        // WHEN
        List<MTTRMetric> result = mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, periodType);

        // THEN: The result should have a periodEnd matching the partial rangeEnd
        assertEquals(1, result.size());
        MTTRMetric partialBiweekMetric = result.get(0);
        assertEquals(0, partialBiweekMetric.getIncidentCount());
        assertEquals(biweekStart, partialBiweekMetric.getPeriodStart());
        assertEquals(rangeEnd, partialBiweekMetric.getPeriodEnd()); // Verify the range is correctly clipped
    }

    @Test
    void testCalculate_withUnsupportedPeriodType_returnsEmptyList() {
        // GIVEN: A valid range but an unsupported periodType (null)
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);

        // WHEN
        List<MTTRMetric> result = mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, null);

        // THEN: The result should be an empty list and no interaction with the repository should occur
        assertEquals(0, result.size());
        verify(incidentRepository, never()).findByServiceNameAndStateAndStartTimeBetween(any(), any(), any(), any());
    }

    @Test
    void testCalculate_onlyResolvedIncidentsAreCounted() {
        // GIVEN: A repository is configured to return only RESOLVED incidents
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);
        PeriodType periodType = PeriodType.MONTHLY;

        RepositoryConfig repo = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);

        List<Incident> resolvedIncidents = List.of(
            createIncident("inc-1", repo, LocalDate.of(2025, 11, 5), 300L)
        );

        when(incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(resolvedIncidents);

        // WHEN
        mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, periodType);

        // THEN: Verify that the repository was queried specifically for RESOLVED state
        verify(incidentRepository).findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                any(LocalDateTime.class),
                any(LocalDateTime.class));

        // Verify that ACTIVE and STABLE states were never queried
        verify(incidentRepository, never()).findByServiceNameAndStateAndStartTimeBetween(
                anyString(),
                eq(IncidentState.ACTIVE),
                any(LocalDateTime.class),
                any(LocalDateTime.class));
        verify(incidentRepository, never()).findByServiceNameAndStateAndStartTimeBetween(
                anyString(),
                eq(IncidentState.STABLE),
                any(LocalDateTime.class),
                any(LocalDateTime.class));
    }

    @Test
    void testCalculate_handlesIncidentsWithNullDuration() {
        // GIVEN: Incidents where some have null duration (should be treated as 0)
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);
        PeriodType periodType = PeriodType.MONTHLY;

        RepositoryConfig repo = new RepositoryConfig("https://github.com/test/repo", SERVICE_NAME);

        List<Incident> incidentsWithNullDuration = List.of(
            createIncident("inc-1", repo, LocalDate.of(2025, 11, 5), 600L),  // 10 min
            createIncidentWithNullDuration("inc-2", repo, LocalDate.of(2025, 11, 15)), // null duration
            createIncident("inc-3", repo, LocalDate.of(2025, 11, 25), 600L)   // 10 min
        );

        when(incidentRepository.findByServiceNameAndStateAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(IncidentState.RESOLVED),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(incidentsWithNullDuration);

        // WHEN
        List<MTTRMetric> result = mttrCalculationService.calculate(SERVICE_NAME, rangeStart, rangeEnd, periodType);

        // THEN: Should handle null duration gracefully (treat as 0)
        assertEquals(1, result.size());
        MTTRMetric metric = result.get(0);
        assertEquals(3, metric.getIncidentCount());
        // Average: (600 + 0 + 600) / 3 = 400 seconds
        assertEquals(400L, metric.getAverageDurationSeconds());
    }

    // Helper methods to create test data

    private Incident createIncident(String id, RepositoryConfig repo, LocalDate startDate, Long durationSeconds) {
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime resolvedTime = startTime.plusSeconds(durationSeconds);

        return new Incident(
                id,
                repo,
                "Test Incident",
                IncidentState.RESOLVED,
                null,
                startTime,
                resolvedTime,
                durationSeconds,
                SERVICE_NAME,
                startTime,
                resolvedTime
        );
    }

    private Incident createIncidentWithNullDuration(String id, RepositoryConfig repo, LocalDate startDate) {
        LocalDateTime startTime = startDate.atStartOfDay();

        return new Incident(
                id,
                repo,
                "Test Incident",
                IncidentState.RESOLVED,
                null,
                startTime,
                null,  // null resolved time
                null,  // null duration
                SERVICE_NAME,
                startTime,
                startTime
        );
    }
}
