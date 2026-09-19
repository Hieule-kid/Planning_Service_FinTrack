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
 */
public final class MilestoneCalculator {

    private static final DateTimeFormatter MONTH_YEAR_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private MilestoneCalculator() {
        throw new UnsupportedOperationException("MilestoneCalculator is a utility class");
    }

    public static List<MilestoneEntity> generateSchedule(CreatePlanRequest request) {
        validateCombination(request);

        int periodCount = resolvePeriodCount(request);
        if (periodCount <= 0) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Plan duration must produce at least one milestone");
        }
        if (periodCount > 3650) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Plan would generate " + periodCount + " milestones which exceeds the maximum of 3650. " +
                    "Use a longer interval (e.g. MONTHLY instead of DAILY).");
        }

        BigDecimal targetAmount = request.getTargetAmount();
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
        } else if (category == TimeframeCategory.MID_TERM) {
            if (frequency != Frequency.DAILY && frequency != Frequency.MONTHLY) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                        "Mid-term plans only support DAILY or MONTHLY frequency");
            }
            if (request.getDurationInMonths() == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                        "durationInMonths is required for mid-term plans");
            }
        } else if (category == TimeframeCategory.LONG_TERM) {
            if (frequency != Frequency.MONTHLY && frequency != Frequency.ANNUALLY) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                        "Long-term plans only support MONTHLY or ANNUALLY frequency");
            }
            if (request.getDurationInMonths() == null && request.getDurationInYears() == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                        "durationInMonths or durationInYears is required for long-term plans");
            }
        } else {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Unsupported timeframe category");
        }
    }

    private static int resolvePeriodCount(CreatePlanRequest request) {
        int effectiveMonths = request.getDurationInMonths() != null
                ? request.getDurationInMonths()
                : request.getDurationInYears() * 12;

        return switch (request.getFrequency()) {
            case DAILY -> (int) ChronoUnit.DAYS.between(
                    request.getStartDate(),
                    request.getStartDate().plusMonths(effectiveMonths));
            case MONTHLY -> effectiveMonths;
            case ANNUALLY -> request.getDurationInYears() != null
                    ? request.getDurationInYears()
                    : (effectiveMonths + 11) / 12;
        };
    }

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

    private record IntervalDates(String timeline, LocalDate periodDate, LocalDate deadline) {
    }

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

    public static MilestoneStatus deriveStatus(MilestoneEntity milestone, BigDecimal effectiveTarget, LocalDate now) {
        if (milestone.isManuallyCompleted() || milestone.getActualSaved().compareTo(effectiveTarget) >= 0) {
            return MilestoneStatus.COMPLETED;
        }
        if (now.isAfter(milestone.getDeadline())) {
            return MilestoneStatus.OVERDUE;
        }
        return MilestoneStatus.PENDING;
    }

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

    public record LiveMilestone(MilestoneEntity milestone, BigDecimal targetSavings, MilestoneStatus status) {
    }

    public record PlanTotals(BigDecimal totalSaved, BigDecimal remaining, BigDecimal progressPercent) {
    }
}
