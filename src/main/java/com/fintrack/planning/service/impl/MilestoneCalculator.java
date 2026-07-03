package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.request.CreatePlanRequest;
import com.fintrack.planning.model.MilestoneEntity;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.MilestoneStatus;
import com.fintrack.planning.model.enums.TimeframeCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stateless pure-math helper for the Financial Planning feature.
 *
 * <p>Deliberately kept free of Spring / persistence concerns so its algorithms —
 * milestone schedule generation, live status derivation, and deficit
 * redistribution — can be unit tested in isolation.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public final class MilestoneCalculator {

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    /** Prevent instantiation. */
    private MilestoneCalculator() {
        throw new UnsupportedOperationException("MilestoneCalculator is a utility class");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Schedule generation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates the full milestone schedule for a new plan, splitting the target
     * amount evenly across intervals with any remainder allocated to the last milestone.
     *
     * <p>The returned entities are not yet attached to a {@code PlanEntity} or persisted —
     * the caller is responsible for setting {@code plan} before saving.
     *
     * @param request the validated plan-creation request
     * @return the generated milestones, in schedule order, each with {@code sequenceIndex} set
     * @throws AppException with {@link ErrorCode#INVALID_REQUEST} if the timeframe/frequency/duration
     *                       combination is invalid
     */
    public static List<MilestoneEntity> generateSchedule(CreatePlanRequest request) {
        validateCombination(request);

        int periodCount = resolvePeriodCount(request);
        if (periodCount <= 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Plan duration must produce at least one milestone");
        }

        BigDecimal targetAmount = request.getTargetAmount();
        // Floor-divide so we never over-allocate; the remainder is added to the last milestone.
        BigDecimal baseShare = targetAmount.divide(BigDecimal.valueOf(periodCount), 2, RoundingMode.DOWN);
        BigDecimal remainder = targetAmount.subtract(baseShare.multiply(BigDecimal.valueOf(periodCount)));

        List<MilestoneEntity> milestones = new ArrayList<>(periodCount);
        for (int i = 0; i < periodCount; i++) {
            boolean isLast = i == periodCount - 1;
            BigDecimal allocation = isLast ? baseShare.add(remainder) : baseShare;

            IntervalDates dates = resolveIntervalDates(request.getStartDate(), request.getFrequency(), i);

            milestones.add(MilestoneEntity.builder()
                    .sequenceIndex(i)
                    .timeline(dates.timeline())
                    .periodDate(dates.periodDate())
                    .deadline(dates.deadline())
                    .baseTargetSavings(allocation)
                    .targetSavings(allocation)
                    .actualSaved(BigDecimal.ZERO)
                    .manuallyCompleted(false)
                    .status(MilestoneStatus.PENDING)
                    .build());
        }
        return milestones;
    }

    /**
     * Validates that the timeframe category, frequency, and duration field combination
     * on the request is internally consistent.
     *
     * @param request the plan-creation request
     * @throws AppException with {@link ErrorCode#INVALID_REQUEST} if invalid
     */
    private static void validateCombination(CreatePlanRequest request) {
        TimeframeCategory category = request.getTimeframeCategory();
        Frequency frequency = request.getFrequency();

        if (category == TimeframeCategory.SHORT_TERM) {
            if (frequency != Frequency.DAILY && frequency != Frequency.MONTHLY) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                        "Short-term plans only support DAILY or MONTHLY frequency");
            }
            if (request.getDurationInMonths() == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                        "durationInMonths is required for short-term plans");
            }
        } else if (category == TimeframeCategory.LONG_TERM) {
            if (frequency != Frequency.MONTHLY && frequency != Frequency.ANNUALLY) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                        "Long-term plans only support MONTHLY or ANNUALLY frequency");
            }
            if (request.getDurationInYears() == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                        "durationInYears is required for long-term plans");
            }
        } else {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Unsupported timeframe category");
        }
    }

    /**
     * Computes the total number of milestone intervals for the plan.
     *
     * @param request the plan-creation request
     * @return the number of milestones to generate
     */
    private static int resolvePeriodCount(CreatePlanRequest request) {
        return switch (request.getFrequency()) {
            case DAILY -> (int) ChronoUnit.DAYS.between(
                    request.getStartDate(),
                    request.getStartDate().plusMonths(request.getDurationInMonths()));
            case MONTHLY -> request.getTimeframeCategory() == TimeframeCategory.SHORT_TERM
                    ? request.getDurationInMonths()
                    : request.getDurationInYears() * 12;
            case ANNUALLY -> request.getDurationInYears();
        };
    }

    /**
     * Computes the timeline label, period date, and deadline for the {@code index}-th
     * (zero-based) interval of the schedule.
     *
     * @param startDate the plan's start date
     * @param frequency the recurrence interval
     * @param index     zero-based interval position
     * @return the resolved dates and label for this interval
     */
    private static IntervalDates resolveIntervalDates(LocalDate startDate, Frequency frequency, int index) {
        return switch (frequency) {
            case DAILY -> {
                LocalDate day = startDate.plusDays(index);
                yield new IntervalDates("Day " + (index + 1), day, day);
            }
            case MONTHLY -> {
                YearMonth month = YearMonth.from(startDate).plusMonths(index);
                yield new IntervalDates(
                        month.format(MONTH_YEAR_FORMATTER),
                        month.atDay(1),
                        month.atEndOfMonth());
            }
            case ANNUALLY -> {
                int year = startDate.getYear() + index;
                yield new IntervalDates(
                        "Year " + (index + 1) + " (" + year + ")",
                        LocalDate.of(year, 1, 1),
                        LocalDate.of(year, 12, 31));
            }
        };
    }

    /** Small holder for the three date-derived attributes of a schedule interval. */
    private record IntervalDates(String timeline, LocalDate periodDate, LocalDate deadline) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Live status + deficit redistribution
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Computes the live, effective target savings and status for every milestone in a plan,
     * applying deficit redistribution when {@code recalculateOnMissedDeadline} is enabled.
     *
     * <p>Redistribution algorithm: every milestone whose deadline has passed (without being
     * completed) contributes its deficit ({@code baseTargetSavings - actualSaved}) to a pool.
     * That pool is split evenly — with any remainder on the last receiver — across all
     * milestones that are still pending (deadline not yet passed, not completed), added on
     * top of their own {@code baseTargetSavings}. If there are no such future milestones,
     * the pool is left unredistributed (nothing changes).
     *
     * @param milestones                  the plan's milestones, in any order
     * @param recalculateOnMissedDeadline whether redistribution is enabled for this plan
     * @param now                         the current date to evaluate against
     * @return one {@link LiveMilestone} per input milestone, in the same order
     */
    public static List<LiveMilestone> computeLiveMilestones(
            List<MilestoneEntity> milestones, boolean recalculateOnMissedDeadline, LocalDate now) {

        if (!recalculateOnMissedDeadline) {
            List<LiveMilestone> result = new ArrayList<>(milestones.size());
            for (MilestoneEntity m : milestones) {
                BigDecimal effectiveTarget = m.getBaseTargetSavings();
                result.add(new LiveMilestone(m, effectiveTarget, deriveStatus(m, effectiveTarget, now)));
            }
            return result;
        }

        // First pass: determine each milestone's status against its own base allocation,
        // to identify which are overdue (deficit sources) and which are future/pending (receivers).
        MilestoneStatus[] baseStatuses = new MilestoneStatus[milestones.size()];
        for (int i = 0; i < milestones.size(); i++) {
            baseStatuses[i] = deriveStatus(milestones.get(i), milestones.get(i).getBaseTargetSavings(), now);
        }

        BigDecimal totalDeficit = BigDecimal.ZERO;
        for (int i = 0; i < milestones.size(); i++) {
            if (baseStatuses[i] == MilestoneStatus.OVERDUE) {
                MilestoneEntity m = milestones.get(i);
                BigDecimal deficit = m.getBaseTargetSavings().subtract(m.getActualSaved());
                if (deficit.compareTo(BigDecimal.ZERO) > 0) {
                    totalDeficit = totalDeficit.add(deficit);
                }
            }
        }

        List<Integer> receiverIndexes = new ArrayList<>();
        for (int i = 0; i < milestones.size(); i++) {
            if (baseStatuses[i] == MilestoneStatus.PENDING) {
                receiverIndexes.add(i);
            }
        }

        BigDecimal share = BigDecimal.ZERO;
        BigDecimal shareRemainder = BigDecimal.ZERO;
        if (!receiverIndexes.isEmpty() && totalDeficit.compareTo(BigDecimal.ZERO) > 0) {
            share = totalDeficit.divide(BigDecimal.valueOf(receiverIndexes.size()), 2, RoundingMode.DOWN);
            shareRemainder = totalDeficit.subtract(share.multiply(BigDecimal.valueOf(receiverIndexes.size())));
        }

        int lastReceiverIndex = receiverIndexes.isEmpty() ? -1 : receiverIndexes.get(receiverIndexes.size() - 1);

        List<LiveMilestone> result = new ArrayList<>(milestones.size());
        for (int i = 0; i < milestones.size(); i++) {
            MilestoneEntity m = milestones.get(i);
            BigDecimal effectiveTarget = m.getBaseTargetSavings();

            if (baseStatuses[i] == MilestoneStatus.PENDING && share.compareTo(BigDecimal.ZERO) > 0) {
                effectiveTarget = effectiveTarget.add(share);
                if (i == lastReceiverIndex) {
                    effectiveTarget = effectiveTarget.add(shareRemainder);
                }
            }

            result.add(new LiveMilestone(m, effectiveTarget, deriveStatus(m, effectiveTarget, now)));
        }
        return result;
    }

    /**
     * Derives the live status of a milestone against a given effective target.
     *
     * <p>Precedence: completed (by amount or manual flag) &gt; overdue (deadline passed) &gt; pending.
     *
     * @param milestone       the milestone entity (for {@code actualSaved}, {@code manuallyCompleted}, {@code deadline})
     * @param effectiveTarget the target savings to compare {@code actualSaved} against
     * @param now             the current date
     * @return the derived {@link MilestoneStatus}
     */
    public static MilestoneStatus deriveStatus(MilestoneEntity milestone, BigDecimal effectiveTarget, LocalDate now) {
        if (milestone.isManuallyCompleted() || milestone.getActualSaved().compareTo(effectiveTarget) >= 0) {
            return MilestoneStatus.COMPLETED;
        }
        if (now.isAfter(milestone.getDeadline())) {
            return MilestoneStatus.OVERDUE;
        }
        return MilestoneStatus.PENDING;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Totals
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Computes the plan-level totals from a set of already-live-evaluated milestones.
     *
     * @param liveMilestones the live milestones (see {@link #computeLiveMilestones})
     * @param targetAmount   the plan's overall target amount
     * @return the computed {@link PlanTotals}
     */
    public static PlanTotals computeTotals(List<LiveMilestone> liveMilestones, BigDecimal targetAmount) {
        BigDecimal totalSaved = BigDecimal.ZERO;
        for (LiveMilestone live : liveMilestones) {
            if (live.status() == MilestoneStatus.COMPLETED) {
                totalSaved = totalSaved.add(live.milestone().getActualSaved());
            }
        }

        BigDecimal remaining = targetAmount.subtract(totalSaved);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        BigDecimal progressPercent;
        if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            progressPercent = BigDecimal.ZERO;
        } else {
            progressPercent = totalSaved
                    .divide(targetAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
            if (progressPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
                progressPercent = BigDecimal.valueOf(100).setScale(2, RoundingMode.HALF_UP);
            }
        }

        return new PlanTotals(totalSaved, remaining, progressPercent);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Value holders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * The live-computed target savings and status for one milestone.
     *
     * @param milestone       the underlying persisted entity
     * @param targetSavings   the effective (possibly redistributed) target savings
     * @param status          the live-derived status
     */
    public record LiveMilestone(MilestoneEntity milestone, BigDecimal targetSavings, MilestoneStatus status) {
    }

    /**
     * The rolled-up totals for a plan.
     *
     * @param totalSaved      sum of {@code actualSaved} for completed milestones
     * @param remaining       {@code max(0, targetAmount - totalSaved)}
     * @param progressPercent {@code min(100, totalSaved / targetAmount * 100)}
     */
    public record PlanTotals(BigDecimal totalSaved, BigDecimal remaining, BigDecimal progressPercent) {
    }
}
