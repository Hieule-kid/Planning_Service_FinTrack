package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.planning.dto.request.CreatePlanRequest;
import com.fintrack.planning.model.MilestoneEntity;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.MilestoneStatus;
import com.fintrack.planning.model.enums.TimeframeCategory;
import org.junit.jupiter.api.Test;

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
    // Schedule generation
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
    void deriveStatus_pendingWhenNotYetDue() {
        MilestoneEntity milestone = milestone("100.00", "10.00", false, LocalDate.of(2027, 12, 31));
        MilestoneStatus status = MilestoneCalculator.deriveStatus(milestone, milestone.getBaseTargetSavings(), LocalDate.of(2027, 1, 1));
        assertThat(status).isEqualTo(MilestoneStatus.PENDING);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deficit redistribution
    // ─────────────────────────────────────────────────────────────────────────

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
        // Redistribution disabled — future milestone's target is untouched.
        assertThat(live.get(1).targetSavings()).isEqualByComparingTo("100.00");
    }

    @Test
    void computeLiveMilestones_redistributesDeficitEvenlyAcrossFutureMilestones() {
        LocalDate now = LocalDate.of(2027, 3, 1);
        // Deadline passed, nothing saved -> full 100 deficit.
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
    void computeLiveMilestones_noRedistributionWhenAllFutureMilestonesAlsoOverdue() {
        LocalDate now = LocalDate.of(2027, 12, 1);
        MilestoneEntity overdue1 = milestone("100.00", "0.00", false, LocalDate.of(2027, 1, 1));
        MilestoneEntity overdue2 = milestone("100.00", "0.00", false, LocalDate.of(2027, 6, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overdue1, overdue2), true, now);

        // No pending/future receivers exist — deficits accumulate but are not redistributed.
        assertThat(live.get(0).targetSavings()).isEqualByComparingTo("100.00");
        assertThat(live.get(0).status()).isEqualTo(MilestoneStatus.OVERDUE);
        assertThat(live.get(1).targetSavings()).isEqualByComparingTo("100.00");
        assertThat(live.get(1).status()).isEqualTo(MilestoneStatus.OVERDUE);
    }

    @Test
    void computeLiveMilestones_noRedistributionWhenZeroFutureMilestonesRemain() {
        LocalDate now = LocalDate.of(2027, 12, 1);
        // Only one milestone total, and it is overdue -> nothing to redistribute to.
        MilestoneEntity overdue = milestone("200.00", "50.00", false, LocalDate.of(2027, 1, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overdue), true, now);

        assertThat(live).hasSize(1);
        assertThat(live.get(0).targetSavings()).isEqualByComparingTo("200.00");
        assertThat(live.get(0).status()).isEqualTo(MilestoneStatus.OVERDUE);
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
        // Saved more than the target amount for this single milestone.
        MilestoneEntity overSaved = milestone("100.00", "150.00", false, LocalDate.of(2027, 1, 1));

        List<MilestoneCalculator.LiveMilestone> live =
                MilestoneCalculator.computeLiveMilestones(List.of(overSaved), false, now);

        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, new BigDecimal("100.00"));

        assertThat(totals.totalSaved()).isEqualByComparingTo("150.00");
        assertThat(totals.remaining()).isEqualByComparingTo("0.00");
        assertThat(totals.progressPercent()).isEqualByComparingTo("100.00");
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
