package com.fintrack.planning.model.enums;

/**
 * The recurrence interval used to split a savings goal into milestones.
 *
 * <p>Valid combinations with {@link TimeframeCategory}:
 * <ul>
 *   <li>{@code SHORT_TERM} → {@link #DAILY} or {@link #MONTHLY}</li>
 *   <li>{@code LONG_TERM} → {@link #MONTHLY} or {@link #ANNUALLY}</li>
 * </ul>
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public enum Frequency {

    /** One milestone per calendar day. Only valid for short-term plans. */
    DAILY,

    /** One milestone per calendar month. Valid for both timeframe categories. */
    MONTHLY,

    /** One milestone per calendar year. Only valid for long-term plans. */
    ANNUALLY
}
