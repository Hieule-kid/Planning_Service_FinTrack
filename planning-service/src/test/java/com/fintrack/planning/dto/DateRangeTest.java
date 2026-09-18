package com.fintrack.planning.dto;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DateRange#resolve(LocalDate, LocalDate)} — defaulting and
 * the partial / reversed / oversized guard rails.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
class DateRangeTest {

    @Test
    void resolve_bothNull_defaultsToLast31DaysInclusive() {
        LocalDate today = LocalDate.now();

        DateRange range = DateRange.resolve(null, null);

        assertThat(range.to()).isEqualTo(today);
        assertThat(range.from()).isEqualTo(today.minusDays(30));
    }

    @Test
    void resolve_exactly31DayRange_passes() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = from.plusDays(30); // 31 calendar days inclusive

        DateRange range = DateRange.resolve(from, to);

        assertThat(range.from()).isEqualTo(from);
        assertThat(range.to()).isEqualTo(to);
    }

    @Test
    void resolve_32DayRange_isRejected() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = from.plusDays(31); // 32 calendar days inclusive

        assertThatThrownBy(() -> DateRange.resolve(from, to))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DATE_RANGE_TOO_LARGE);
    }

    @Test
    void resolve_reversedDates_isRejected() {
        LocalDate from = LocalDate.of(2026, 3, 10);
        LocalDate to = LocalDate.of(2026, 3, 1);

        assertThatThrownBy(() -> DateRange.resolve(from, to))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    void resolve_onlyFromSupplied_isRejected() {
        assertThatThrownBy(() -> DateRange.resolve(LocalDate.of(2026, 3, 1), null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARTIAL_DATE_RANGE);
    }

    @Test
    void resolve_onlyToSupplied_isRejected() {
        assertThatThrownBy(() -> DateRange.resolve(null, LocalDate.of(2026, 3, 1)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARTIAL_DATE_RANGE);
    }
}
