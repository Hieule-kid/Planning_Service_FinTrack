package com.fintrack.planning.repository;

import java.math.BigDecimal;

/**
 * Projection of {@link PlanRepository#summarizeByUser(String)}: one row per currency.
 */
public interface CurrencyTotals {

    String getCurrency();

    long getPlanCount();

    long getCompletedPlanCount();

    BigDecimal getTotalTarget();

    BigDecimal getTotalSaved();

    BigDecimal getRemaining();
}
