package com.fintrack.planning.dto.response;

import com.fintrack.planning.model.enums.Currency;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.PlanCategory;
import com.fintrack.planning.model.enums.TimeframeCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full response DTO for a single plan — includes the plan's fields, its complete
 * milestone schedule, and rolled-up totals.
 *
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {

    private String id;
    private String goalTitle;
    private BigDecimal targetAmount;
    private Currency currency;
    private PlanCategory planCategory;
    private TimeframeCategory timeframeCategory;
    private Integer durationInMonths;
    private Integer durationInYears;
    private Frequency frequency;
    private BigDecimal requiredPerPeriod;
    private LocalDate startDate;
    private boolean recalculateOnMissedDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<MilestoneResponse> milestones;

    private BigDecimal totalSaved;

    private BigDecimal remaining;

    private BigDecimal progressPercent;
}
