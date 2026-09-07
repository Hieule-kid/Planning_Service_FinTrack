package com.fintrack.planning.model.enums;

/**
 * How an expense row entered the system.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public enum ExpenseSource {

    /** Entered by the user through the expense form. */
    MANUAL,

    /** Created by a CSV import job. */
    CSV_IMPORT
}
