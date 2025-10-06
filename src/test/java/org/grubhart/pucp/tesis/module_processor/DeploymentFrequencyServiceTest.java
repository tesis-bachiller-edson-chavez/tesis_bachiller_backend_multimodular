package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.Deployment;
import org.grubhart.pucp.tesis.module_domain.DeploymentRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeploymentFrequencyServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @InjectMocks
    private DeploymentFrequencyService deploymentFrequencyService;

    @Test
    void testCalculate_monthlyPeriod_returnsCorrectCount() {
        // GIVEN: A date range for a single month and a list of 3 deployments within that month
        String environment = "production";
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);
        PeriodType periodType = PeriodType.MONTHLY;

        List<Deployment> deploymentsInNovember = List.of(
            new Deployment(1L, "deploy-1", "sha1", "main", environment, "success", "success", LocalDate.of(2025, 11, 5).atStartOfDay(), LocalDate.of(2025, 11, 5).atStartOfDay()),
            new Deployment(2L, "deploy-2", "sha2", "main", environment, "success", "success", LocalDate.of(2025, 11, 15).atStartOfDay(), LocalDate.of(2025, 11, 15).atStartOfDay()),
            new Deployment(3L, "deploy-3", "sha3", "main", environment, "success", "success", LocalDate.of(2025, 11, 25).atStartOfDay(), LocalDate.of(2025, 11, 25).atStartOfDay())
        );

        when(deploymentRepository.findByEnvironmentAndCreatedAtBetween(environment, rangeStart, rangeEnd))
                .thenReturn(deploymentsInNovember);

        // WHEN: the service calculates the frequency for that monthly period
        List<DeploymentFrequency> result = deploymentFrequencyService.calculate(environment, rangeStart, rangeEnd, periodType);

        // THEN: the result should be a list with a single metric object for that month with a count of 3
        assertEquals(1, result.size());
        DeploymentFrequency novemberMetric = result.get(0);
        assertEquals(3, novemberMetric.getCount());
        assertEquals(LocalDate.of(2025, 11, 1), novemberMetric.getPeriodStart());
        assertEquals(LocalDate.of(2025, 11, 30), novemberMetric.getPeriodEnd());
    }

    @Test
    void testCalculate_weeklyPeriod_returnsCorrectCount() {
        // GIVEN: A date range for a single week (Week 45 of 2025: Nov 3 to Nov 9)
        String environment = "production";
        LocalDate rangeStart = LocalDate.of(2025, 11, 3);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 9);
        PeriodType periodType = PeriodType.WEEKLY;

        List<Deployment> deploymentsInWeek45 = List.of(
            new Deployment(4L, "deploy-4", "sha4", "main", environment, "success", "success", LocalDate.of(2025, 11, 4).atStartOfDay(), LocalDate.of(2025, 11, 4).atStartOfDay()),
            new Deployment(5L, "deploy-5", "sha5", "main", environment, "success", "success", LocalDate.of(2025, 11, 8).atStartOfDay(), LocalDate.of(2025, 11, 8).atStartOfDay())
        );

        // The service will calculate the boundaries of the week and query for it
        LocalDate expectedWeekStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate expectedWeekEnd = rangeEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        when(deploymentRepository.findByEnvironmentAndCreatedAtBetween(environment, expectedWeekStart, expectedWeekEnd))
                .thenReturn(deploymentsInWeek45);

        // WHEN
        List<DeploymentFrequency> result = deploymentFrequencyService.calculate(environment, rangeStart, rangeEnd, periodType);

        // THEN: The result should be a single metric for that week with a count of 2
        assertEquals(1, result.size());
        DeploymentFrequency week45Metric = result.get(0);
        assertEquals(2, week45Metric.getCount());
        assertEquals(expectedWeekStart, week45Metric.getPeriodStart());
        assertEquals(expectedWeekEnd, week45Metric.getPeriodEnd());
    }

    @Test
    void testCalculate_biweeklyPeriod_returnsCorrectCount() {
        // GIVEN: A date range for a bi-weekly period (Nov 3 to Nov 16, 2025)
        String environment = "production";
        LocalDate rangeStart = LocalDate.of(2025, 11, 3);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 16);
        PeriodType periodType = PeriodType.BIWEEKLY;

        List<Deployment> deploymentsInBiweek = List.of(
            new Deployment(6L, "d6", "s6", "main", environment, "s", "s", LocalDate.of(2025, 11, 4).atStartOfDay(), null),
            new Deployment(7L, "d7", "s7", "main", environment, "s", "s", LocalDate.of(2025, 11, 8).atStartOfDay(), null),
            new Deployment(8L, "d8", "s8", "main", environment, "s", "s", LocalDate.of(2025, 11, 11).atStartOfDay(), null),
            new Deployment(9L, "d9", "s9", "main", environment, "s", "s", LocalDate.of(2025, 11, 15).atStartOfDay(), null)
        );

        // The service will calculate the boundaries of the bi-weekly period
        LocalDate expectedBiweeklyStart = LocalDate.of(2025, 11, 3);
        LocalDate expectedBiweeklyEnd = LocalDate.of(2025, 11, 16);

        when(deploymentRepository.findByEnvironmentAndCreatedAtBetween(environment, expectedBiweeklyStart, expectedBiweeklyEnd))
                .thenReturn(deploymentsInBiweek);

        // WHEN
        List<DeploymentFrequency> result = deploymentFrequencyService.calculate(environment, rangeStart, rangeEnd, periodType);

        // THEN: The result should be a single metric for that bi-weekly period with a count of 4
        assertEquals(1, result.size());
        DeploymentFrequency biweeklyMetric = result.get(0);
        assertEquals(4, biweeklyMetric.getCount());
        assertEquals(expectedBiweeklyStart, biweeklyMetric.getPeriodStart());
        assertEquals(expectedBiweeklyEnd, biweeklyMetric.getPeriodEnd());
    }

    @Test
    void testCalculate_monthlyPeriodWithPartialEndRange_usesCorrectEndDate() {
        // GIVEN: A date range that ends before the end of the month
        String environment = "production";
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 15); // Partial range
        PeriodType periodType = PeriodType.MONTHLY;

        LocalDate monthStart = LocalDate.of(2025, 11, 1);

        // The service should query from the start of the month to the specified (partial) end date
        when(deploymentRepository.findByEnvironmentAndCreatedAtBetween(environment, monthStart, rangeEnd))
                .thenReturn(Collections.emptyList());

        // WHEN
        List<DeploymentFrequency> result = deploymentFrequencyService.calculate(environment, rangeStart, rangeEnd, periodType);

        // THEN: The result should have a periodEnd matching the partial rangeEnd
        assertEquals(1, result.size());
        DeploymentFrequency partialMonthMetric = result.get(0);
        assertEquals(0, partialMonthMetric.getCount());
        assertEquals(monthStart, partialMonthMetric.getPeriodStart());
        assertEquals(rangeEnd, partialMonthMetric.getPeriodEnd()); // Verify the range is correctly clipped
    }

    @Test
    void testCalculate_weeklyPeriodWithPartialEndRange_usesCorrectEndDate() {
        // GIVEN: A date range that ends before the end of the week
        String environment = "production";
        LocalDate rangeStart = LocalDate.of(2025, 11, 3); // Monday
        LocalDate rangeEnd = LocalDate.of(2025, 11, 5);   // Wednesday
        PeriodType periodType = PeriodType.WEEKLY;

        LocalDate weekStart = LocalDate.of(2025, 11, 3);

        when(deploymentRepository.findByEnvironmentAndCreatedAtBetween(environment, weekStart, rangeEnd))
                .thenReturn(Collections.emptyList());

        // WHEN
        List<DeploymentFrequency> result = deploymentFrequencyService.calculate(environment, rangeStart, rangeEnd, periodType);

        // THEN: The result should have a periodEnd matching the partial rangeEnd
        assertEquals(1, result.size());
        DeploymentFrequency partialWeekMetric = result.get(0);
        assertEquals(0, partialWeekMetric.getCount());
        assertEquals(weekStart, partialWeekMetric.getPeriodStart());
        assertEquals(rangeEnd, partialWeekMetric.getPeriodEnd()); // Verify the range is correctly clipped
    }

    @Test
    void testCalculate_biweeklyPeriodWithPartialEndRange_usesCorrectEndDate() {
        // GIVEN: A date range that ends before the end of the bi-weekly period
        String environment = "production";
        LocalDate rangeStart = LocalDate.of(2025, 11, 3); // Bi-week starts on Monday
        LocalDate rangeEnd = LocalDate.of(2025, 11, 10);  // Partial range ends on the next Monday
        PeriodType periodType = PeriodType.BIWEEKLY;

        LocalDate biweekStart = LocalDate.of(2025, 11, 3);

        when(deploymentRepository.findByEnvironmentAndCreatedAtBetween(environment, biweekStart, rangeEnd))
                .thenReturn(Collections.emptyList());

        // WHEN
        List<DeploymentFrequency> result = deploymentFrequencyService.calculate(environment, rangeStart, rangeEnd, periodType);

        // THEN: The result should have a periodEnd matching the partial rangeEnd
        assertEquals(1, result.size());
        DeploymentFrequency partialBiweekMetric = result.get(0);
        assertEquals(0, partialBiweekMetric.getCount());
        assertEquals(biweekStart, partialBiweekMetric.getPeriodStart());
        assertEquals(rangeEnd, partialBiweekMetric.getPeriodEnd()); // Verify the range is correctly clipped
    }

    @Test
    void testCalculate_withUnsupportedPeriodType_returnsEmptyList() {
        // GIVEN: A valid range but an unsupported periodType (null)
        String environment = "production";
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);

        // WHEN
        List<DeploymentFrequency> result = deploymentFrequencyService.calculate(environment, rangeStart, rangeEnd, null);

        // THEN: The result should be an empty list and no interaction with the repository should occur
        assertEquals(0, result.size());
        verify(deploymentRepository, never()).findByEnvironmentAndCreatedAtBetween(any(), any(), any());
    }
}
