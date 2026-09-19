package com.fintrack.planning.dto;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * An inclusive {@code [from, to]} calendar-date window, used to bound expense list queries.
 *
 * @param from window start, inclusive
 * @param to   window end, inclusive
 */
public record DateRange(LocalDate from, LocalDate to) {

    private static final long MAX_DAYS_BETWEEN = 31;

    private static final long DEFAULT_LOOKBACK_DAYS = 30;

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
