package com.fintrack.planning.repository;

import java.math.BigDecimal;

/**
 * Projection of {@link ExpenseRepository#summarizeByCurrency}: one row per currency.
 */
public interface ExpenseCurrencyTotals {

    String getCurrency();

    long getExpenseCount();

    BigDecimal getTotal();

    BigDecimal getFixed();

    BigDecimal getVariable();
}
