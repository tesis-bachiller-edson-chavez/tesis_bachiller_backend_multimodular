package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.DeploymentRepository;
import org.grubhart.pucp.tesis.module_domain.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CFRCalculationServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private CFRCalculationService cfrCalculationService;

    private static final String SERVICE_NAME = "tesis-backend";
    private static final String ENVIRONMENT = "production";

    @Test
    void testCalculate_monthlyPeriod_returnsCorrectCFR() {
        // GIVEN: A date range for a single month with 10 deployments and 2 incidents
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);
        PeriodType periodType = PeriodType.MONTHLY;

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(
                eq(ENVIRONMENT),
                eq(rangeStart),
                eq(rangeEnd)))
                .thenReturn(10L);

        when(incidentRepository.countByServiceNameAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(rangeStart.atStartOfDay()),
                eq(rangeEnd.atTime(23, 59, 59))))
                .thenReturn(2L);

        // WHEN: the service calculates CFR for that monthly period
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, periodType);

        // THEN: the result should be a list with a single metric object for that month
        assertEquals(1, result.size());
        CFRMetric novemberMetric = result.get(0);
        assertEquals(10L, novemberMetric.getDeploymentCount());
        assertEquals(2L, novemberMetric.getIncidentCount());
        // CFR = 2 / 10 = 0.20 = 20%
        assertEquals(0.20, novemberMetric.getRate(), 0.001);
        assertEquals(20.0, novemberMetric.getRatePercentage(), 0.001);
        assertEquals("High", novemberMetric.getDoraLevel());
        assertEquals(LocalDate.of(2025, 11, 1), novemberMetric.getPeriodStart());
        assertEquals(LocalDate.of(2025, 11, 30), novemberMetric.getPeriodEnd());
    }

    @Test
    void testCalculate_weeklyPeriod_returnsCorrectCFR() {
        // GIVEN: A date range for a single week with 5 deployments and 1 incident
        LocalDate rangeStart = LocalDate.of(2025, 11, 3);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 9);
        PeriodType periodType = PeriodType.WEEKLY;

        LocalDate expectedWeekStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate expectedWeekEnd = rangeEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(
                eq(ENVIRONMENT),
                eq(expectedWeekStart),
                eq(expectedWeekEnd)))
                .thenReturn(5L);

        when(incidentRepository.countByServiceNameAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(expectedWeekStart.atStartOfDay()),
                eq(expectedWeekEnd.atTime(23, 59, 59))))
                .thenReturn(1L);

        // WHEN
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, periodType);

        // THEN: The result should be a single metric for that week
        assertEquals(1, result.size());
        CFRMetric week45Metric = result.get(0);
        assertEquals(5L, week45Metric.getDeploymentCount());
        assertEquals(1L, week45Metric.getIncidentCount());
        // CFR = 1 / 5 = 0.20 = 20%
        assertEquals(0.20, week45Metric.getRate(), 0.001);
        assertEquals("High", week45Metric.getDoraLevel());
        assertEquals(expectedWeekStart, week45Metric.getPeriodStart());
        assertEquals(expectedWeekEnd, week45Metric.getPeriodEnd());
    }

    @Test
    void testCalculate_biweeklyPeriod_returnsCorrectCFR() {
        // GIVEN: A date range for a bi-weekly period with 8 deployments and 1 incident
        LocalDate rangeStart = LocalDate.of(2025, 11, 3);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 16);
        PeriodType periodType = PeriodType.BIWEEKLY;

        LocalDate expectedBiweeklyStart = LocalDate.of(2025, 11, 3);
        LocalDate expectedBiweeklyEnd = LocalDate.of(2025, 11, 16);

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(
                eq(ENVIRONMENT),
                eq(expectedBiweeklyStart),
                eq(expectedBiweeklyEnd)))
                .thenReturn(8L);

        when(incidentRepository.countByServiceNameAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(expectedBiweeklyStart.atStartOfDay()),
                eq(expectedBiweeklyEnd.atTime(23, 59, 59))))
                .thenReturn(1L);

        // WHEN
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, periodType);

        // THEN: The result should be a single metric for that bi-weekly period
        assertEquals(1, result.size());
        CFRMetric biweeklyMetric = result.get(0);
        assertEquals(8L, biweeklyMetric.getDeploymentCount());
        assertEquals(1L, biweeklyMetric.getIncidentCount());
        // CFR = 1 / 8 = 0.125 = 12.5%
        assertEquals(0.125, biweeklyMetric.getRate(), 0.001);
        assertEquals(12.5, biweeklyMetric.getRatePercentage(), 0.001);
        assertEquals("Elite", biweeklyMetric.getDoraLevel());
        assertEquals(expectedBiweeklyStart, biweeklyMetric.getPeriodStart());
        assertEquals(expectedBiweeklyEnd, biweeklyMetric.getPeriodEnd());
    }

    @Test
    void testCalculate_noDeployments_returnsZeroCFR() {
        // GIVEN: A date range with no deployments but 3 incidents
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);
        PeriodType periodType = PeriodType.MONTHLY;

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(any(), any(), any()))
                .thenReturn(0L);

        when(incidentRepository.countByServiceNameAndStartTimeBetween(any(), any(), any()))
                .thenReturn(3L);

        // WHEN
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, periodType);

        // THEN: The result should have zero CFR (can't divide by zero deployments)
        assertEquals(1, result.size());
        CFRMetric metric = result.get(0);
        assertEquals(0L, metric.getDeploymentCount());
        assertEquals(3L, metric.getIncidentCount());
        assertEquals(0.0, metric.getRate(), 0.001);
        assertEquals(0.0, metric.getRatePercentage(), 0.001);
        assertEquals("Elite", metric.getDoraLevel()); // 0% is Elite
    }

    @Test
    void testCalculate_noIncidents_returnsZeroCFR() {
        // GIVEN: A date range with 10 deployments but no incidents
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);
        PeriodType periodType = PeriodType.MONTHLY;

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(any(), any(), any()))
                .thenReturn(10L);

        when(incidentRepository.countByServiceNameAndStartTimeBetween(any(), any(), any()))
                .thenReturn(0L);

        // WHEN
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, periodType);

        // THEN: The result should have zero CFR (perfect!)
        assertEquals(1, result.size());
        CFRMetric metric = result.get(0);
        assertEquals(10L, metric.getDeploymentCount());
        assertEquals(0L, metric.getIncidentCount());
        assertEquals(0.0, metric.getRate(), 0.001);
        assertEquals(0.0, metric.getRatePercentage(), 0.001);
        assertEquals("Elite", metric.getDoraLevel());
    }

    @Test
    void testCalculate_multipleIncidentsPerDeployment_returnsRateGreaterThanOne() {
        // GIVEN: A date range with 5 deployments but 8 incidents (CFR > 1.0)
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);
        PeriodType periodType = PeriodType.MONTHLY;

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(any(), any(), any()))
                .thenReturn(5L);

        when(incidentRepository.countByServiceNameAndStartTimeBetween(any(), any(), any()))
                .thenReturn(8L);

        // WHEN
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, periodType);

        // THEN: CFR can be > 1.0 when there are multiple incidents per deployment
        assertEquals(1, result.size());
        CFRMetric metric = result.get(0);
        assertEquals(5L, metric.getDeploymentCount());
        assertEquals(8L, metric.getIncidentCount());
        // CFR = 8 / 5 = 1.6 = 160%
        assertEquals(1.6, metric.getRate(), 0.001);
        assertEquals(160.0, metric.getRatePercentage(), 0.001);
        assertEquals("Low", metric.getDoraLevel());
    }

    @Test
    void testCalculate_monthlyPeriodWithPartialEndRange_usesCorrectEndDate() {
        // GIVEN: A date range that ends before the end of the month
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 15); // Partial range
        PeriodType periodType = PeriodType.MONTHLY;

        LocalDate monthStart = LocalDate.of(2025, 11, 1);

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(
                eq(ENVIRONMENT),
                eq(monthStart),
                eq(rangeEnd)))
                .thenReturn(0L);

        when(incidentRepository.countByServiceNameAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(monthStart.atStartOfDay()),
                eq(rangeEnd.atTime(23, 59, 59))))
                .thenReturn(0L);

        // WHEN
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, periodType);

        // THEN: The result should have a periodEnd matching the partial rangeEnd
        assertEquals(1, result.size());
        CFRMetric partialMonthMetric = result.get(0);
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

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(
                eq(ENVIRONMENT),
                eq(weekStart),
                eq(rangeEnd)))
                .thenReturn(0L);

        when(incidentRepository.countByServiceNameAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(weekStart.atStartOfDay()),
                eq(rangeEnd.atTime(23, 59, 59))))
                .thenReturn(0L);

        // WHEN
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, periodType);

        // THEN: The result should have a periodEnd matching the partial rangeEnd
        assertEquals(1, result.size());
        CFRMetric partialWeekMetric = result.get(0);
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

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(
                eq(ENVIRONMENT),
                eq(biweekStart),
                eq(rangeEnd)))
                .thenReturn(0L);

        when(incidentRepository.countByServiceNameAndStartTimeBetween(
                eq(SERVICE_NAME),
                eq(biweekStart.atStartOfDay()),
                eq(rangeEnd.atTime(23, 59, 59))))
                .thenReturn(0L);

        // WHEN
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, periodType);

        // THEN: The result should have a periodEnd matching the partial rangeEnd
        assertEquals(1, result.size());
        CFRMetric partialBiweekMetric = result.get(0);
        assertEquals(biweekStart, partialBiweekMetric.getPeriodStart());
        assertEquals(rangeEnd, partialBiweekMetric.getPeriodEnd()); // Verify the range is correctly clipped
    }

    @Test
    void testCalculate_withUnsupportedPeriodType_returnsEmptyList() {
        // GIVEN: A valid range but an unsupported periodType (null)
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);

        // WHEN
        List<CFRMetric> result = cfrCalculationService.calculate(SERVICE_NAME, ENVIRONMENT, rangeStart, rangeEnd, null);

        // THEN: The result should be an empty list and no interaction with the repositories should occur
        assertEquals(0, result.size());
        verify(deploymentRepository, never()).countByEnvironmentAndCreatedAtBetween(any(), any(), any());
        verify(incidentRepository, never()).countByServiceNameAndStartTimeBetween(any(), any(), any());
    }

    @Test
    void testDoraLevelClassification() {
        // Test DORA level classification boundaries

        // Elite: <= 15%
        CFRMetric eliteMetric = new CFRMetric(LocalDate.now(), LocalDate.now(), 100, 10, 0.10);
        assertEquals("Elite", eliteMetric.getDoraLevel());
        assertEquals(10.0, eliteMetric.getRatePercentage(), 0.001);

        // High: 16-30%
        CFRMetric highMetric = new CFRMetric(LocalDate.now(), LocalDate.now(), 100, 25, 0.25);
        assertEquals("High", highMetric.getDoraLevel());
        assertEquals(25.0, highMetric.getRatePercentage(), 0.001);

        // Medium: 31-45%
        CFRMetric mediumMetric = new CFRMetric(LocalDate.now(), LocalDate.now(), 100, 40, 0.40);
        assertEquals("Medium", mediumMetric.getDoraLevel());
        assertEquals(40.0, mediumMetric.getRatePercentage(), 0.001);

        // Low: > 45%
        CFRMetric lowMetric = new CFRMetric(LocalDate.now(), LocalDate.now(), 100, 50, 0.50);
        assertEquals("Low", lowMetric.getDoraLevel());
        assertEquals(50.0, lowMetric.getRatePercentage(), 0.001);
    }

    @Test
    void testCalculate_usesCorrectEnvironmentFilter() {
        // GIVEN: A specific environment filter
        String customEnvironment = "staging";
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(any(), any(), any()))
                .thenReturn(5L);
        when(incidentRepository.countByServiceNameAndStartTimeBetween(any(), any(), any()))
                .thenReturn(1L);

        // WHEN
        cfrCalculationService.calculate(SERVICE_NAME, customEnvironment, rangeStart, rangeEnd, PeriodType.MONTHLY);

        // THEN: Should query deployments with the correct environment
        verify(deploymentRepository).countByEnvironmentAndCreatedAtBetween(
                eq(customEnvironment),
                any(LocalDate.class),
                any(LocalDate.class));
    }

    @Test
    void testCalculate_usesCorrectServiceNameFilter() {
        // GIVEN: A specific service name
        String customService = "custom-service";
        LocalDate rangeStart = LocalDate.of(2025, 11, 1);
        LocalDate rangeEnd = LocalDate.of(2025, 11, 30);

        when(deploymentRepository.countByEnvironmentAndCreatedAtBetween(any(), any(), any()))
                .thenReturn(5L);
        when(incidentRepository.countByServiceNameAndStartTimeBetween(any(), any(), any()))
                .thenReturn(1L);

        // WHEN
        cfrCalculationService.calculate(customService, ENVIRONMENT, rangeStart, rangeEnd, PeriodType.MONTHLY);

        // THEN: Should query incidents with the correct service name
        verify(incidentRepository).countByServiceNameAndStartTimeBetween(
                eq(customService),
                any(),
                any());
    }
}
