package com.fintrack.planning.dto;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @Test
    void resolve_bothNull_isUnbounded() {
        DateRange range = DateRange.resolve(null, null);

        assertThat(range.from()).isNull();
        assertThat(range.to()).isNull();
    }

    @Test
    void resolve_sameDay_passes() {
        LocalDate day = LocalDate.of(2026, 3, 1);

        DateRange range = DateRange.resolve(day, day);

        assertThat(range.from()).isEqualTo(day);
        assertThat(range.to()).isEqualTo(day);
    }

    @Test
    void resolve_hasNoMaximumSpan() {
        LocalDate from = LocalDate.of(2020, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        DateRange range = DateRange.resolve(from, to);

        assertThat(range.from()).isEqualTo(from);
        assertThat(range.to()).isEqualTo(to);
    }

    @Test
    void resolve_reversedDates_isRejected() {
        assertThatThrownBy(() -> DateRange.resolve(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1)))
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
