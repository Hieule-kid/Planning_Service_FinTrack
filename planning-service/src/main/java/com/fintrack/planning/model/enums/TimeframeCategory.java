package com.fintrack.planning.model.enums;

/**
 * Categorises a savings goal by its overall time horizon.
 *
 * <p>Determines the valid range for the plan's duration and which
 * {@link Frequency} values are permitted:
 * <ul>
 *   <li>{@link #SHORT_TERM} — 3 to 11 months, {@code DAILY} or {@code MONTHLY} frequency</li>
 *   <li>{@link #MID_TERM}   — 12 to 60 months, {@code DAILY} or {@code MONTHLY} frequency</li>
 *   <li>{@link #LONG_TERM}  — 61 to 240 months, {@code MONTHLY} or {@code ANNUALLY} frequency</li>
 * </ul>
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public enum TimeframeCategory {

    /** Goal duration between 3 and 11 months. */
    SHORT_TERM,

    /** Goal duration between 12 and 60 months (1–5 years). */
    MID_TERM,

    /** Goal duration between 61 and 240 months (5–20 years). */
    LONG_TERM
}
