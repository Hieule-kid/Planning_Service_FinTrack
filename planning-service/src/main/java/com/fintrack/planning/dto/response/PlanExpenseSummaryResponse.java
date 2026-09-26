package com.fintrack.planning.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Rollup of the expenses a user has linked to a single plan.
 *
 * <p>{@code totalSpent} only sums expenses recorded in the plan's own currency — an expense
 * filed in a different currency cannot be added to the total without a conversion rate, so it
 * is left out of the sum and counted in {@code excludedCount} instead, rather than silently
 * mixing currencies together.
 *
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanExpenseSummaryResponse {

    private String planId;
    private String currency;
    private BigDecimal totalSpent;
    private long expenseCount;
    private long excludedCount;
}
