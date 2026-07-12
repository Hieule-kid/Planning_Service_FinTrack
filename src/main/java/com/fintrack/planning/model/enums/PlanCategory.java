package com.fintrack.planning.model.enums;

/**
 * Semantic category for a savings plan, matching the eight options
 * presented in the FE plan-creation form.
 *
 * <p>Serialized as uppercase strings by default (e.g. {@code "TRAVEL"}).
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public enum PlanCategory {

    EMERGENCY,
    TRAVEL,
    HOUSE,
    CAR,
    EDUCATION,
    WEDDING,
    RETIREMENT,
    OTHER;
}
