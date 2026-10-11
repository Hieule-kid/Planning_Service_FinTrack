package com.fintrack.planning.dto;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;

import java.time.LocalDate;

/**
 * An inclusive {@code [from, to]} calendar-date window used to bound expense queries. Both bounds
 * are {@code null} when the caller supplied no dates, meaning "all time" (no date restriction).
 *
 * @param from window start, inclusive; {@code null} = unbounded
 * @param to   window end, inclusive; {@code null} = unbounded
 */
public record DateRange(LocalDate from, LocalDate to) {

    /**
     * Supply both dates or neither. There is no maximum span.
     *
     * @throws AppException {@code PARTIAL_DATE_RANGE} if only one date is given,
     *                      {@code INVALID_DATE_RANGE} if {@code to} is before {@code from}
     */
    public static DateRange resolve(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return new DateRange(null, null);
        }

        if (from == null || to == null) {
            throw new AppException(ErrorCode.PARTIAL_DATE_RANGE);
        }

        if (to.isBefore(from)) {
            throw new AppException(ErrorCode.INVALID_DATE_RANGE);
        }

        return new DateRange(from, to);
    }
}
