package org.grubhart.pucp.tesis.module_processor;

import org.grubhart.pucp.tesis.module_domain.Deployment;
import org.grubhart.pucp.tesis.module_domain.DeploymentRepository;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

@Service
public class DeploymentFrequencyService {

    private final DeploymentRepository deploymentRepository;

    public DeploymentFrequencyService(DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
    }

    public List<DeploymentFrequency> calculate(String environment, LocalDate rangeStart, LocalDate rangeEnd, PeriodType periodType) {

        List<DeploymentFrequency> results = new ArrayList<>();

        if (periodType == PeriodType.MONTHLY) {
            LocalDate currentMonthStart = rangeStart.with(TemporalAdjusters.firstDayOfMonth());

            while (!currentMonthStart.isAfter(rangeEnd)) {
                LocalDate currentMonthEnd = currentMonthStart.with(TemporalAdjusters.lastDayOfMonth());

                // Asegurarse de no exceder el rango final solicitado
                if (currentMonthEnd.isAfter(rangeEnd)) {
                    currentMonthEnd = rangeEnd;
                }

                List<Deployment> deployments = deploymentRepository.findByEnvironmentAndCreatedAtBetween(environment, currentMonthStart, currentMonthEnd);
                int count = deployments.size();

                DeploymentFrequency metric = new DeploymentFrequency(currentMonthStart, currentMonthEnd, count);
                results.add(metric);

                // Avanzar al siguiente mes
                currentMonthStart = currentMonthStart.plusMonths(1);
            }
        } else if (periodType == PeriodType.WEEKLY) {
            LocalDate currentWeekStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            while (!currentWeekStart.isAfter(rangeEnd)) {
                LocalDate currentWeekEnd = currentWeekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

                // Asegurarse de no exceder el rango final solicitado
                if (currentWeekEnd.isAfter(rangeEnd)) {
                    currentWeekEnd = rangeEnd;
                }

                List<Deployment> deployments = deploymentRepository.findByEnvironmentAndCreatedAtBetween(environment, currentWeekStart, currentWeekEnd);
                int count = deployments.size();

                DeploymentFrequency metric = new DeploymentFrequency(currentWeekStart, currentWeekEnd, count);
                results.add(metric);

                // Avanzar a la siguiente semana
                currentWeekStart = currentWeekStart.plusWeeks(1);
            }
        } else if (periodType == PeriodType.BIWEEKLY) {
            LocalDate currentBiweeklyStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

            while (!currentBiweeklyStart.isAfter(rangeEnd)) {
                LocalDate currentBiweeklyEnd = currentBiweeklyStart.plusWeeks(2).minusDays(1);

                // Asegurarse de no exceder el rango final solicitado
                if (currentBiweeklyEnd.isAfter(rangeEnd)) {
                    currentBiweeklyEnd = rangeEnd;
                }

                List<Deployment> deployments = deploymentRepository.findByEnvironmentAndCreatedAtBetween(environment, currentBiweeklyStart, currentBiweeklyEnd);
                int count = deployments.size();

                DeploymentFrequency metric = new DeploymentFrequency(currentBiweeklyStart, currentBiweeklyEnd, count);
                results.add(metric);

                // Avanzar al siguiente período quincenal
                currentBiweeklyStart = currentBiweeklyStart.plusWeeks(2);
            }
        }

        return results;
    }
}
