package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.planning.dto.request.CreatePlanRequest;
import com.fintrack.planning.model.MilestoneEntity;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.MilestoneStatus;
import com.fintrack.planning.model.enums.TimeframeCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MilestoneCalculator} — the highest-risk pure business logic
 * in the Financial Planning feature: schedule generation, live status derivation,
 * and deficit redistribution.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
class MilestoneCalculatorTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Schedule generation — happy paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void generateSchedule_singleMilestone_longTermAnnual() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Down Payment");
        request.setTargetAmount(new BigDecimal("1000.00"));
        request.setTimeframeCategory(TimeframeCategory.LONG_TERM);
        request.setDurationInYears(1);
        request.setFrequency(Frequency.ANNUALLY);
        request.setRequiredPerPeriod(new BigDecimal("1000.00"));
        request.setStartDate(LocalDate.of(2027, 1, 15));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        assertThat(schedule).hasSize(1);
        MilestoneEntity only = schedule.get(0);
        assertThat(only.getBaseTargetSavings()).isEqualByComparingTo("1000.00");
        assertThat(only.getTimeline()).isEqualTo("Year 1 (2027)");
        assertThat(only.getPeriodDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(only.getDeadline()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(only.getSequenceIndex()).isZero();
    }

    @Test
    void generateSchedule_remainderIsAllocatedToLastMilestone() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Vacation Fund");
        request.setTargetAmount(new BigDecimal("100.00"));
        request.setTimeframeCategory(TimeframeCategory.SHORT_TERM);
        request.setDurationInMonths(3);
        request.setFrequency(Frequency.MONTHLY);
        request.setRequiredPerPeriod(new BigDecimal("33.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        assertThat(schedule).hasSize(3);
        // 100 / 3 = 33.33 (floor) each, remainder 0.01 -> last milestone gets 33.34
        assertThat(schedule.get(0).getBaseTargetSavings()).isEqualByComparingTo("33.33");
        assertThat(schedule.get(1).getBaseTargetSavings()).isEqualByComparingTo("33.33");
        assertThat(schedule.get(2).getBaseTargetSavings()).isEqualByComparingTo("33.34");

        BigDecimal sum = schedule.stream()
                .map(MilestoneEntity::getBaseTargetSavings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("100.00");

        assertThat(schedule.get(0).getTimeline()).isEqualTo("January 2027");
        assertThat(schedule.get(1).getTimeline()).isEqualTo("February 2027");
        assertThat(schedule.get(2).getTimeline()).isEqualTo("March 2027");
    }

    @Test
    void generateSchedule_evenlyDivisibleAmount_noRemainder() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Car Fund");
        request.setTargetAmount(new BigDecimal("300.00"));
        request.setTimeframeCategory(TimeframeCategory.SHORT_TERM);
        request.setDurationInMonths(3);
        request.setFrequency(Frequency.MONTHLY);
        request.setRequiredPerPeriod(new BigDecimal("100.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        assertThat(schedule).hasSize(3);
        // 300 / 3 = 100.00 each, no remainder
        assertThat(schedule.get(0).getBaseTargetSavings()).isEqualByComparingTo("100.00");
        assertThat(schedule.get(1).getBaseTargetSavings()).isEqualByComparingTo("100.00");
        assertThat(schedule.get(2).getBaseTargetSavings()).isEqualByComparingTo("100.00");

        BigDecimal sum = schedule.stream()
                .map(MilestoneEntity::getBaseTargetSavings)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("300.00");
    }

    @Test
    void generateSchedule_dailyFrequency_generatesOneMilestonePerDay() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Coffee Fund");
        request.setTargetAmount(new BigDecimal("30.00"));
        request.setTimeframeCategory(TimeframeCategory.SHORT_TERM);
        request.setDurationInMonths(3);
        request.setFrequency(Frequency.DAILY);
        request.setRequiredPerPeriod(new BigDecimal("1.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        LocalDate expectedEnd = LocalDate.of(2027, 1, 1).plusMonths(3);
        int expectedDays = (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.of(2027, 1, 1), expectedEnd);

        assertThat(schedule).hasSize(expectedDays);
        assertThat(schedule.get(0).getTimeline()).isEqualTo("Day 1");
        assertThat(schedule.get(0).getPeriodDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(schedule.get(0).getDeadline()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(schedule.get(1).getTimeline()).isEqualTo("Day 2");
    }

    @Test
    void generateSchedule_midTermMonthly_generatesCorrectSchedule() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("House Deposit");
        request.setTargetAmount(new BigDecimal("12000.00"));
        request.setTimeframeCategory(TimeframeCategory.MID_TERM);
        request.setDurationInMonths(12);
        request.setFrequency(Frequency.MONTHLY);
        request.setRequiredPerPeriod(new BigDecimal("1000.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        assertThat(schedule).hasSize(12);
        assertThat(schedule.get(0).getTimeline()).isEqualTo("January 2027");
        assertThat(schedule.get(11).getTimeline()).isEqualTo("December 2027");
        assertThat(schedule.get(0).getPeriodDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(schedule.get(0).getDeadline()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(schedule.get(1).getPeriodDate()).isEqualTo(LocalDate.of(2027, 2, 1));
    }

    @Test
    void generateSchedule_midTermDaily_generatesOneMilestonePerDay() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Wedding Fund");
        request.setTargetAmount(new BigDecimal("365.00"));
        request.setTimeframeCategory(TimeframeCategory.MID_TERM);
        request.setDurationInMonths(12);
        request.setFrequency(Frequency.DAILY);
        request.setRequiredPerPeriod(new BigDecimal("1.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        int expectedDays = (int) java.time.temporal.ChronoUnit.DAYS.between(
                LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 1).plusMonths(12));
        assertThat(schedule).hasSize(expectedDays);
        assertThat(schedule.get(0).getTimeline()).isEqualTo("Day 1");
    }

    @Test
    void generateSchedule_longTermMonthly_useDurationInMonths_whenNoDurationInYears() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Retirement");
        request.setTargetAmount(new BigDecimal("1200.00"));
        request.setTimeframeCategory(TimeframeCategory.LONG_TERM);
        request.setDurationInMonths(12); // 12 months, no durationInYears
        request.setFrequency(Frequency.MONTHLY);
        request.setRequiredPerPeriod(new BigDecimal("100.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        assertThat(schedule).hasSize(12);
    }

    @Test
    void generateSchedule_longTermAnnually_derivePeriodCountFromDurationInMonths() {
        // durationInYears is null → effectiveMonths / 12 = 24 / 12 = 2
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Education");
        request.setTargetAmount(new BigDecimal("2000.00"));
        request.setTimeframeCategory(TimeframeCategory.LONG_TERM);
        request.setDurationInMonths(24);
        request.setFrequency(Frequency.ANNUALLY);
        request.setRequiredPerPeriod(new BigDecimal("1000.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        assertThat(schedule).hasSize(2);
        assertThat(schedule.get(0).getTimeline()).isEqualTo("Year 1 (2027)");
        assertThat(schedule.get(1).getTimeline()).isEqualTo("Year 2 (2028)");
    }

    @Test
    void generateSchedule_multipleAnnualMilestones_labelsAreCorrect() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("20-Year Plan");
        request.setTargetAmount(new BigDecimal("20000.00"));
        request.setTimeframeCategory(TimeframeCategory.LONG_TERM);
        request.setDurationInYears(3);
        request.setFrequency(Frequency.ANNUALLY);
        request.setRequiredPerPeriod(new BigDecimal("6666.67"));
        request.setStartDate(LocalDate.of(2027, 6, 15));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        assertThat(schedule).hasSize(3);
        assertThat(schedule.get(0).getTimeline()).isEqualTo("Year 1 (2027)");
        assertThat(schedule.get(1).getTimeline()).isEqualTo("Year 2 (2028)");
        assertThat(schedule.get(2).getTimeline()).isEqualTo("Year 3 (2029)");

        assertThat(schedule.get(0).getPeriodDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(schedule.get(0).getDeadline()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(schedule.get(1).getPeriodDate()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(schedule.get(1).getDeadline()).isEqualTo(LocalDate.of(2028, 12, 31));
    }

    @Test
    void generateSchedule_allMilestonesHaveCorrectInitialState() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Emergency");
        request.setTargetAmount(new BigDecimal("600.00"));
        request.setTimeframeCategory(TimeframeCategory.SHORT_TERM);
        request.setDurationInMonths(6);
        request.setFrequency(Frequency.MONTHLY);
        request.setRequiredPerPeriod(new BigDecimal("100.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);

        for (int i = 0; i < schedule.size(); i++) {
            MilestoneEntity m = schedule.get(i);
            assertThat(m.getSequenceIndex()).isEqualTo(i);
            assertThat(m.getActualSaved()).isEqualByComparingTo("0.00");
            assertThat(m.isManuallyCompleted()).isFalse();
            assertThat(m.getStatus()).isEqualTo(MilestoneStatus.PENDING);
            assertThat(m.getBaseTargetSavings()).isEqualByComparingTo(m.getTargetSavings());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schedule generation — validation rejections
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void generateSchedule_rejectsInvalidFrequencyForShortTerm() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Bad Combo");
        request.setTargetAmount(new BigDecimal("100.00"));
        request.setTimeframeCategory(TimeframeCategory.SHORT_TERM);
        request.setDurationInMonths(6);
        request.setFrequency(Frequency.ANNUALLY);
        request.setRequiredPerPeriod(new BigDecimal("10.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        assertThatThrownBy(() -> MilestoneCalculator.generateSchedule(request))
                .isInstanceOf(AppException.class);
    }

    @Test
    void generateSchedule_rejectsShortTermMissingDurationInMonths() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Bad Combo");
        request.setTargetAmount(new BigDecimal("100.00"));
        request.setTimeframeCategory(TimeframeCategory.SHORT_TERM);
        request.setFrequency(Frequency.MONTHLY);
        request.setRequiredPerPeriod(new BigDecimal("10.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));
        // durationInMonths intentionally null

        assertThatThrownBy(() -> MilestoneCalculator.generateSchedule(request))
                .isInstanceOf(AppException.class);
    }

    @Test
    void generateSchedule_rejectsMidTermAnnuallyFrequency() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Bad Combo");
        request.setTargetAmount(new BigDecimal("100.00"));
        request.setTimeframeCategory(TimeframeCategory.MID_TERM);
        request.setDurationInMonths(24);
        request.setFrequency(Frequency.ANNUALLY);
        request.setRequiredPerPeriod(new BigDecimal("50.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        assertThatThrownBy(() -> MilestoneCalculator.generateSchedule(request))
                .isInstanceOf(AppException.class);
    }

    @Test
    void generateSchedule_rejectsMidTermMissingDurationInMonths() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Bad Combo");
        request.setTargetAmount(new BigDecimal("100.00"));
        request.setTimeframeCategory(TimeframeCategory.MID_TERM);
        request.setFrequency(Frequency.MONTHLY);
        request.setRequiredPerPeriod(new BigDecimal("10.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));
        // durationInMonths intentionally null

        assertThatThrownBy(() -> MilestoneCalculator.generateSchedule(request))
                .isInstanceOf(AppException.class);
    }

    @Test
    void generateSchedule_rejectsLongTermDailyFrequency() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Bad Combo");
        request.setTargetAmount(new BigDecimal("1000.00"));
        request.setTimeframeCategory(TimeframeCategory.LONG_TERM);
        request.setDurationInYears(5);
        request.setFrequency(Frequency.DAILY);
        request.setRequiredPerPeriod(new BigDecimal("100.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        assertThatThrownBy(() -> MilestoneCalculator.generateSchedule(request))
                .isInstanceOf(AppException.class);
    }

    @Test
    void generateSchedule_rejectsLongTermMissingBothDurationFields() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Bad Combo");
        request.setTargetAmount(new BigDecimal("1000.00"));
        request.setTimeframeCategory(TimeframeCategory.LONG_TERM);
        request.setFrequency(Frequency.ANNUALLY);
        request.setRequiredPerPeriod(new BigDecimal("100.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));
        // both durationInMonths and durationInYears null

        assertThatThrownBy(() -> MilestoneCalculator.generateSchedule(request))
                .isInstanceOf(AppException.class);
    }

    @Test
    void generateSchedule_rejectsNullTimeframeCategory() {
        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Bad Combo");
        request.setTargetAmount(new BigDecimal("100.00"));
        request.setTimeframeCategory(null);
        request.setDurationInMonths(6);
        request.setFrequency(Frequency.MONTHLY);
        request.setRequiredPerPeriod(new BigDecimal("10.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        assertThatThrownBy(() -> MilestoneCalculator.generateSchedule(request))
                .isInstanceOf(AppException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Live status derivation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deriveStatus_completedWhenActualSavedMeetsTarget() {
        MilestoneEntity milestone = milestone("100.00", "100.00", false, LocalDate.of(2027, 6, 1));
        MilestoneStatus status = MilestoneCalculator.deriveStatus(milestone, milestone.getBaseTargetSavings(), LocalDate.of(2027, 1, 1));
        assertThat(status).isEqualTo(MilestoneStatus.COMPLETED);
    }

    @Test
    void deriveStatus_completedWhenActualSavedExceedsTarget() {
        MilestoneEntity milestone = milestone("100.00", "150.00", false, LocalDate.of(2027, 6, 1));
        MilestoneStatus status = MilestoneCalculator.deriveStatus(milestone, new BigDecimal("100.00"), LocalDate.of(2027, 1, 1));
        assertThat(status).isEqualTo(MilestoneStatus.COMPLETED);
    }

    @Test
    void deriveStatus_completedWhenManuallyMarkedEvenIfUnderfunded() {
        MilestoneEntity milestone = milestone("100.00", "10.00", true, LocalDate.of(2027, 1, 1));
        MilestoneStatus status = MilestoneCalculator.deriveStatus(milestone, milestone.getBaseTargetSavings(), LocalDate.of(2027, 6, 1));
        assertThat(status).isEqualTo(MilestoneStatus.COMPLETED);
    }

    @Test
    void deriveStatus_overdueWhenDeadlinePassedAndNotComplete() {
        MilestoneEntity milestone = milestone("100.00", "10.00", false, LocalDate.of(2027, 1, 1));
        MilestoneStatus status = MilestoneCalculator.deriveStatus(milestone, milestone.getBaseTargetSavings(), LocalDate.of(2027, 6, 1));
        assertThat(status).isEqualTo(MilestoneStatus.OVERDUE);
    }

    @Test
    void deriveStatus_overdueWhenZeroSaved() {
        MilestoneEntity milestone = milestone("100.00", "0.00", false, LocalDate.of(2027, 1, 1));
        MilestoneStatus status = MilestoneCalculator.deriveStatus(milestone, new BigDecimal("100.00"), LocalDate.of(2027, 6, 1));
        assertThat(status).isEqualTo(MilestoneStatus.OVERDUE);
    }

    @Test
    void deriveStatus_pendingWhenNotYetDue() {
        MilestoneEntity milestone = milestone("100.00", "10.00", false, LocalDate.of(2027, 12, 31));
        MilestoneStatus status = MilestoneCalculator.deriveStatus(milestone, milestone.getBaseTargetSavings(), LocalDate.of(2027, 1, 1));
        assertThat(status).isEqualTo(MilestoneStatus.PENDING);
    }

    @Test
    void deriveStatus_pendingWhenDeadlineIsToday() {
        // now.isAfter(deadline) is false when deadline == now → PENDING (not overdue yet)
        LocalDate today = LocalDate.of(2027, 6, 1);
        MilestoneEntity milestone = milestone("100.00", "0.00", false, today);
        MilestoneStatus status = MilestoneCalculator.deriveStatus(milestone, new BigDecimal("100.00"), today);
        assertThat(status).isEqualTo(MilestoneStatus.PENDING);
    }

    @Test
    void deriveStatus_completedPrecedesOverdue() {
        // manuallyCompleted=true AND deadline is in the past — completed wins
        LocalDate pastDeadline = LocalDate.of(2027, 1, 1);
        MilestoneEntity milestone = milestone("100.00", "0.00", true, pastDeadline);
        MilestoneStatus status = MilestoneCalculator.deriveStatus(milestone, new BigDecimal("100.00"), LocalDate.of(2027, 6, 1));
        assertThat(status).isEqualTo(MilestoneStatus.COMPLETED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deficit redistribution
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void computeLiveMilestones_emptyList_returnsEmptyList() {
        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(), true, LocalDate.of(2027, 3, 1));
        assertThat(live).isEmpty();
    }

    @Test
    void computeLiveMilestones_noRedistributionWhenDisabled() {
        LocalDate now = LocalDate.of(2027, 3, 1);
        MilestoneEntity overdue = milestone("100.00", "0.00", false, LocalDate.of(2027, 1, 1));
        MilestoneEntity future = milestone("100.00", "0.00", false, LocalDate.of(2027, 6, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overdue, future), false, now);

        assertThat(live.get(0).status()).isEqualTo(MilestoneStatus.OVERDUE);
        assertThat(live.get(0).targetSavings()).isEqualByComparingTo("100.00");
        assertThat(live.get(1).status()).isEqualTo(MilestoneStatus.PENDING);
        assertThat(live.get(1).targetSavings()).isEqualByComparingTo("100.00");
    }

    @Test
    void computeLiveMilestones_redistributesDeficitEvenlyAcrossFutureMilestones() {
        LocalDate now = LocalDate.of(2027, 3, 1);
        MilestoneEntity overdue = milestone("100.00", "0.00", false, LocalDate.of(2027, 1, 1));
        MilestoneEntity future1 = milestone("100.00", "0.00", false, LocalDate.of(2027, 6, 1));
        MilestoneEntity future2 = milestone("100.00", "0.00", false, LocalDate.of(2027, 7, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overdue, future1, future2), true, now);

        assertThat(live.get(0).status()).isEqualTo(MilestoneStatus.OVERDUE);
        assertThat(live.get(0).targetSavings()).isEqualByComparingTo("100.00");

        // 100 deficit split evenly across 2 future milestones -> +50 each
        assertThat(live.get(1).targetSavings()).isEqualByComparingTo("150.00");
        assertThat(live.get(1).status()).isEqualTo(MilestoneStatus.PENDING);
        assertThat(live.get(2).targetSavings()).isEqualByComparingTo("150.00");
        assertThat(live.get(2).status()).isEqualTo(MilestoneStatus.PENDING);
    }

    @Test
    void computeLiveMilestones_remainderFromUnevenSplitGoesToLastReceiver() {
        LocalDate now = LocalDate.of(2027, 3, 1);
        // Deficit of 100 split across 3 receivers -> 33.33 each, remainder 0.01 to the last.
        MilestoneEntity overdue = milestone("100.00", "0.00", false, LocalDate.of(2027, 1, 1));
        MilestoneEntity future1 = milestone("50.00", "0.00", false, LocalDate.of(2027, 6, 1));
        MilestoneEntity future2 = milestone("50.00", "0.00", false, LocalDate.of(2027, 7, 1));
        MilestoneEntity future3 = milestone("50.00", "0.00", false, LocalDate.of(2027, 8, 1));

        List<MilestoneCalculator.LiveMilestone> live = MilestoneCalculator.computeLiveMilestones(
                List.of(overdue, future1, future2, future3), true, now);

        assertThat(live.get(1).targetSavings()).isEqualByComparingTo("83.33");
        assertThat(live.get(2).targetSavings()).isEqualByComparingTo("83.33");
        // Last receiver absorbs the leftover cent so the totals reconcile exactly.
        assertThat(live.get(3).targetSavings()).isEqualByComparingTo("83.34");
    }

    @Test
    void computeLiveMilestones_partialDeficit_onlyUnsavedPortionIsRedistributed() {
        LocalDate now = LocalDate.of(2027, 3, 1);
        // Overdue milestone partially saved: target=100, saved=60, deficit=40
        MilestoneEntity overdue = milestone("100.00", "60.00", false, LocalDate.of(2027, 1, 1));
        MilestoneEntity future = milestone("100.00", "0.00", false, LocalDate.of(2027, 6, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overdue, future), true, now);

        assertThat(live.get(0).status()).isEqualTo(MilestoneStatus.OVERDUE);
        // Future milestone gets the remaining 40 deficit redistributed
        assertThat(live.get(1).targetSavings()).isEqualByComparingTo("140.00");
    }

    @Test
    void computeLiveMilestones_completedMilestoneNotADeficitSource() {
        LocalDate now = LocalDate.of(2027, 3, 1);
        // Completed (manuallyCompleted=true), deadline passed — should NOT contribute deficit
        MilestoneEntity completed = milestone("100.00", "50.00", true, LocalDate.of(2027, 1, 1));
        MilestoneEntity future = milestone("100.00", "0.00", false, LocalDate.of(2027, 6, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(completed, future), true, now);

        assertThat(live.get(0).status()).isEqualTo(MilestoneStatus.COMPLETED);
        // No deficit redistributed — future milestone's target is unchanged
        assertThat(live.get(1).targetSavings()).isEqualByComparingTo("100.00");
    }

    @Test
    void computeLiveMilestones_noRedistributionWhenAllFutureMilestonesAlsoOverdue() {
        LocalDate now = LocalDate.of(2027, 12, 1);
        MilestoneEntity overdue1 = milestone("100.00", "0.00", false, LocalDate.of(2027, 1, 1));
        MilestoneEntity overdue2 = milestone("100.00", "0.00", false, LocalDate.of(2027, 6, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overdue1, overdue2), true, now);

        assertThat(live.get(0).targetSavings()).isEqualByComparingTo("100.00");
        assertThat(live.get(0).status()).isEqualTo(MilestoneStatus.OVERDUE);
        assertThat(live.get(1).targetSavings()).isEqualByComparingTo("100.00");
        assertThat(live.get(1).status()).isEqualTo(MilestoneStatus.OVERDUE);
    }

    @Test
    void computeLiveMilestones_noRedistributionWhenZeroFutureMilestonesRemain() {
        LocalDate now = LocalDate.of(2027, 12, 1);
        MilestoneEntity overdue = milestone("200.00", "50.00", false, LocalDate.of(2027, 1, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overdue), true, now);

        assertThat(live).hasSize(1);
        assertThat(live.get(0).targetSavings()).isEqualByComparingTo("200.00");
        assertThat(live.get(0).status()).isEqualTo(MilestoneStatus.OVERDUE);
    }

    @Test
    void computeLiveMilestones_multipleOverdueWithSharedDeficit_redistributedCorrectly() {
        LocalDate now = LocalDate.of(2027, 4, 1);
        // Two overdue milestones each with 50 deficit = total 100
        MilestoneEntity overdue1 = milestone("100.00", "50.00", false, LocalDate.of(2027, 1, 1));
        MilestoneEntity overdue2 = milestone("100.00", "50.00", false, LocalDate.of(2027, 2, 1));
        MilestoneEntity future = milestone("100.00", "0.00", false, LocalDate.of(2027, 12, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overdue1, overdue2, future), true, now);

        // Each overdue contributes 50 deficit → total 100 redistributed to one future
        assertThat(live.get(2).targetSavings()).isEqualByComparingTo("200.00");
        assertThat(live.get(2).status()).isEqualTo(MilestoneStatus.PENDING);
    }

    @Test
    void computeLiveMilestones_singleAllPendingNoRedistribution() {
        LocalDate now = LocalDate.of(2027, 1, 1);
        MilestoneEntity future1 = milestone("100.00", "0.00", false, LocalDate.of(2027, 6, 1));
        MilestoneEntity future2 = milestone("100.00", "0.00", false, LocalDate.of(2027, 12, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(future1, future2), true, now);

        assertThat(live.get(0).targetSavings()).isEqualByComparingTo("100.00");
        assertThat(live.get(0).status()).isEqualTo(MilestoneStatus.PENDING);
        assertThat(live.get(1).targetSavings()).isEqualByComparingTo("100.00");
        assertThat(live.get(1).status()).isEqualTo(MilestoneStatus.PENDING);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Totals
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void computeTotals_sumsOnlyCompletedMilestonesAndClampsProgress() {
        LocalDate now = LocalDate.of(2027, 3, 1);
        MilestoneEntity completed = milestone("100.00", "100.00", false, LocalDate.of(2027, 1, 1));
        MilestoneEntity pending = milestone("100.00", "10.00", false, LocalDate.of(2027, 6, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(completed, pending), false, now);

        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, new BigDecimal("200.00"));

        assertThat(totals.totalSaved()).isEqualByComparingTo("100.00");
        assertThat(totals.remaining()).isEqualByComparingTo("100.00");
        assertThat(totals.progressPercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void computeTotals_remainingNeverGoesNegativeAndProgressCapsAt100() {
        LocalDate now = LocalDate.of(2027, 3, 1);
        MilestoneEntity overSaved = milestone("100.00", "150.00", false, LocalDate.of(2027, 1, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overSaved), false, now);

        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, new BigDecimal("100.00"));

        assertThat(totals.totalSaved()).isEqualByComparingTo("150.00");
        assertThat(totals.remaining()).isEqualByComparingTo("0.00");
        assertThat(totals.progressPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void computeTotals_allPending_totalSavedIsZeroAndProgressIsZero() {
        LocalDate now = LocalDate.of(2027, 1, 1);
        MilestoneEntity pending1 = milestone("100.00", "0.00", false, LocalDate.of(2027, 6, 1));
        MilestoneEntity pending2 = milestone("100.00", "50.00", false, LocalDate.of(2027, 12, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(pending1, pending2), false, now);

        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, new BigDecimal("200.00"));

        assertThat(totals.totalSaved()).isEqualByComparingTo("0.00");
        assertThat(totals.remaining()).isEqualByComparingTo("200.00");
        assertThat(totals.progressPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void computeTotals_overdueNotCountedTowardsTotalSaved() {
        LocalDate now = LocalDate.of(2027, 6, 1);
        // Deadline passed but not completed
        MilestoneEntity overdue = milestone("100.00", "80.00", false, LocalDate.of(2027, 1, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overdue), false, now);

        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, new BigDecimal("100.00"));

        // OVERDUE milestones do NOT count towards totalSaved
        assertThat(totals.totalSaved()).isEqualByComparingTo("0.00");
        assertThat(totals.remaining()).isEqualByComparingTo("100.00");
        assertThat(totals.progressPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void computeTotals_emptyMilestones_returnsZeroTotals() {
        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(List.of(), new BigDecimal("100.00"));

        assertThat(totals.totalSaved()).isEqualByComparingTo("0.00");
        assertThat(totals.remaining()).isEqualByComparingTo("100.00");
        assertThat(totals.progressPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void computeTotals_zeroTargetAmount_progressPercentIsZero() {
        LocalDate now = LocalDate.of(2027, 3, 1);
        MilestoneEntity completed = milestone("0.00", "0.00", true, LocalDate.of(2027, 1, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(completed), false, now);

        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, BigDecimal.ZERO);

        assertThat(totals.progressPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void computeTotals_multipleCompletedMilestones_sumsAllActualSaved() {
        LocalDate now = LocalDate.of(2027, 6, 1);
        MilestoneEntity c1 = milestone("100.00", "100.00", false, LocalDate.of(2027, 1, 1));
        MilestoneEntity c2 = milestone("100.00", "120.00", false, LocalDate.of(2027, 3, 1));
        MilestoneEntity pending = milestone("100.00", "0.00", false, LocalDate.of(2027, 9, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(c1, c2, pending), false, now);

        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, new BigDecimal("300.00"));

        assertThat(totals.totalSaved()).isEqualByComparingTo("220.00");
        assertThat(totals.remaining()).isEqualByComparingTo("80.00");
        // 220 / 300 * 100 = 73.33%
        assertThat(totals.progressPercent()).isEqualByComparingTo("73.33");
    }

    @ParameterizedTest
    @CsvSource({
        "50.00,  100.00, 50.00",
        "25.00,  100.00, 25.00",
        "10.00,  100.00, 10.00",
        "100.00, 100.00, 100.00"
    })
    void computeTotals_progressPercentIsCorrect(String totalSaved, String target, String expectedPercent) {
        LocalDate now = LocalDate.of(2027, 6, 1);
        MilestoneEntity completed = milestone(totalSaved, totalSaved, false, LocalDate.of(2027, 1, 1));
        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(completed), false, now);
        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, new BigDecimal(target));
        assertThat(totals.progressPercent()).isEqualByComparingTo(expectedPercent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test fixture helper
    // ─────────────────────────────────────────────────────────────────────────

    private static MilestoneEntity milestone(String baseTarget, String actualSaved, boolean manuallyCompleted,
                                              LocalDate deadline) {
        return MilestoneEntity.builder()
                .baseTargetSavings(new BigDecimal(baseTarget))
                .targetSavings(new BigDecimal(baseTarget))
                .actualSaved(new BigDecimal(actualSaved))
                .manuallyCompleted(manuallyCompleted)
                .deadline(deadline)
                .periodDate(deadline)
                .timeline("Test")
                .build();
    }
}
