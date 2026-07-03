package com.fintrack.planning.model.enums;

/**
 * The live, dynamically-derived status of a single milestone.
 *
 * <p><b>Important:</b> this status is always recomputed at read time from
 * {@code actualSaved}, {@code targetSavings}, the manual-completion flag, and
 * the current date — it is never trusted as a static, previously-persisted value.
 * The precedence order is:
 * <ol>
 *   <li>{@link #COMPLETED} — {@code actualSaved >= targetSavings} OR explicitly marked complete</li>
 *   <li>{@link #OVERDUE} — the deadline has passed and the milestone is not complete</li>
 *   <li>{@link #PENDING} — otherwise</li>
 * </ol>
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public enum MilestoneStatus {

    /** Not yet due and not yet complete. */
    PENDING,

    /** Achieved — either by savings meeting the target or manual completion. */
    COMPLETED,

    /** Deadline has passed without the milestone being completed. */
    OVERDUE
}
