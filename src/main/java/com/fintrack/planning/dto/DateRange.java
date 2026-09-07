package com.fintrack.planning.dto;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * An inclusive {@code [from, to]} calendar-date window, used to bound expense list queries.
 *
 * <p>Callers never build this directly from raw request params — they go through
 * {@link #resolve(LocalDate, LocalDate)}, which applies the defaulting and the
 * guard rails (partial range, reversed range, oversized range).
 *
 * @param from window start, inclusive
 * @param to   window end, inclusive
 * @author FinTrack Team
 * @since 1.0.0
 */
public record DateRange(LocalDate from, LocalDate to) {

    /** Maximum span, expressed as the number of days between {@code from} and {@code to}. */
    private static final long MAX_DAYS_BETWEEN = 31;

    /** Default look-back when the caller supplies no dates: 31 calendar days ending today. */
    private static final long DEFAULT_LOOKBACK_DAYS = 30;

    /**
     * Resolves a pair of optional request dates into a validated {@link DateRange}.
     *
     * <ul>
     *   <li>both {@code null} → {@code [today - 30 days, today]} (31 days inclusive)</li>
     *   <li>exactly one {@code null} → {@link ErrorCode#PARTIAL_DATE_RANGE}</li>
     *   <li>{@code to} before {@code from} → {@link ErrorCode#INVALID_DATE_RANGE}</li>
     *   <li>span of 31+ days between the two dates → {@link ErrorCode#DATE_RANGE_TOO_LARGE}
     *       (a 31-day inclusive range passes; 32 days fails)</li>
     * </ul>
     *
     * @param from the requested start date, or {@code null}
     * @param to   the requested end date, or {@code null}
     * @return the validated window
     */
    public static DateRange resolve(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            LocalDate today = LocalDate.now();
            return new DateRange(today.minusDays(DEFAULT_LOOKBACK_DAYS), today);
        }

        if (from == null || to == null) {
            throw new AppException(ErrorCode.PARTIAL_DATE_RANGE);
        }

        if (to.isBefore(from)) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }

        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS_BETWEEN) {
            throw new AppException(ErrorCode.DATE_RANGE_TOO_LARGE);
        }

        return new DateRange(from, to);
    }
}
