package com.fintrack.planning.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Frequency}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
class FrequencyTest {

    @Test
    void shouldContainExactlyThreeValues() {
        assertThat(Frequency.values()).hasSize(3);
    }

    @Test
    void shouldContainDaily() {
        assertThat(Frequency.values()).contains(Frequency.DAILY);
    }

    @Test
    void shouldContainMonthly() {
        assertThat(Frequency.values()).contains(Frequency.MONTHLY);
    }

    @Test
    void shouldContainAnnually() {
        assertThat(Frequency.values()).contains(Frequency.ANNUALLY);
    }

    @Test
    void valueOfDaily_returnsDailyConstant() {
        assertThat(Frequency.valueOf("DAILY")).isEqualTo(Frequency.DAILY);
    }

    @Test
    void valueOfMonthly_returnsMonthlyConstant() {
        assertThat(Frequency.valueOf("MONTHLY")).isEqualTo(Frequency.MONTHLY);
    }

    @Test
    void valueOfAnnually_returnsAnnuallyConstant() {
        assertThat(Frequency.valueOf("ANNUALLY")).isEqualTo(Frequency.ANNUALLY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"daily", "monthly", "annually", "WEEKLY", "QUARTERLY", "", " "})
    void valueOfUnknownName_throwsIllegalArgumentException(String unknown) {
        assertThatThrownBy(() -> Frequency.valueOf(unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueOfNull_throwsNullPointerException() {
        assertThatThrownBy(() -> Frequency.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void dailyOrdinal_isZero() {
        assertThat(Frequency.DAILY.ordinal()).isZero();
    }

    @Test
    void monthlyOrdinal_isOne() {
        assertThat(Frequency.MONTHLY.ordinal()).isOne();
    }

    @Test
    void annuallyOrdinal_isTwo() {
        assertThat(Frequency.ANNUALLY.ordinal()).isEqualTo(2);
    }

    @Test
    void names_matchExpectedStrings() {
        assertThat(Frequency.DAILY.name()).isEqualTo("DAILY");
        assertThat(Frequency.MONTHLY.name()).isEqualTo("MONTHLY");
        assertThat(Frequency.ANNUALLY.name()).isEqualTo("ANNUALLY");
    }

    @Test
    void sameConstant_isSameInstance() {
        assertThat(Frequency.DAILY).isSameAs(Frequency.DAILY);
        assertThat(Frequency.MONTHLY).isSameAs(Frequency.MONTHLY);
        assertThat(Frequency.ANNUALLY).isSameAs(Frequency.ANNUALLY);
    }

    @Test
    void differentConstants_areNotEqual() {
        assertThat(Frequency.DAILY).isNotEqualTo(Frequency.MONTHLY);
        assertThat(Frequency.DAILY).isNotEqualTo(Frequency.ANNUALLY);
        assertThat(Frequency.MONTHLY).isNotEqualTo(Frequency.ANNUALLY);
    }
}
