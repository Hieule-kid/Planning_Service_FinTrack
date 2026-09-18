package com.fintrack.planning.model.enums;

/**
 * Classifies an expense (and the default classification of an expense category)
 * as a recurring fixed cost or a fluctuating variable cost.
 *
 * <p>Shared by both {@code ExpenseCategory} (as its {@code defaultType}) and — in a
 * later slice — {@code Expense} (as a per-row, denormalised {@code expenseType}).
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public enum ExpenseType {

    /** A predictable, recurring cost of roughly constant amount (rent, subscriptions). */
    FIXED,

    /** A cost that varies period to period (groceries, transport, dining out). */
    VARIABLE
}
