package com.fintrack.planning.model.enums;

/**
 * Categorises a savings goal by its overall time horizon.
 *
 * <p>Determines the valid range for the plan's duration and which
 * {@link Frequency} values are permitted:
 * <ul>
 *   <li>{@link #SHORT_TERM} — 3 to 12 months, {@code DAILY} or {@code MONTHLY} frequency</li>
 *   <li>{@link #LONG_TERM} — 1 to 30 years, {@code MONTHLY} or {@code ANNUALLY} frequency</li>
 * </ul>
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public enum TimeframeCategory {

    /** Goal duration between 3 and 12 months. */
    SHORT_TERM,

    /** Goal duration between 1 and 30 years. */
    LONG_TERM
}
