package com.fintrack.planning.dto.response;

import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.TimeframeCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lightweight summary view of a plan, used by the plan-listing endpoint.
 *
 * <p>Omits the full milestone list to keep {@code GET /api/v1/plans} cheap;
 * fetch {@code GET /api/v1/plans/{planId}} for the full {@link PlanResponse}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSummaryResponse {

    private String id;
    private String goalTitle;
    private BigDecimal targetAmount;
    private TimeframeCategory timeframeCategory;
    private Frequency frequency;
    private LocalDate startDate;
    private boolean recalculateOnMissedDeadline;

    private BigDecimal totalSaved;
    private BigDecimal remaining;
    private BigDecimal progressPercent;
}
