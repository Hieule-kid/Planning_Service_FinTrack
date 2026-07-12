package com.fintrack.planning.model.enums;

/**
 * Supported currencies for plan amounts.
 * Mirrors {@code com.fintrack.auth.model.enums.Currency} — kept local to avoid
 * cross-service coupling between planning-service and auth-service.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public enum Currency {

    VND,
    USD;
}
