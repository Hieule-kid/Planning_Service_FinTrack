package com.fintrack.planning.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Currency}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
class CurrencyTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Enum constants
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldContainExactlyTwoValues() {
        assertThat(Currency.values()).hasSize(2);
    }

    @Test
    void shouldContainVnd() {
        assertThat(Currency.values()).contains(Currency.VND);
    }

    @Test
    void shouldContainUsd() {
        assertThat(Currency.values()).contains(Currency.USD);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // valueOf — happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void valueOfVnd_returnsVndConstant() {
        assertThat(Currency.valueOf("VND")).isEqualTo(Currency.VND);
    }

    @Test
    void valueOfUsd_returnsUsdConstant() {
        assertThat(Currency.valueOf("USD")).isEqualTo(Currency.USD);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // valueOf — invalid input
    // ─────────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"vnd", "usd", "EUR", "GBP", "JPY", "", " ", "VND ", " USD"})
    void valueOfUnknownName_throwsIllegalArgumentException(String unknown) {
        assertThatThrownBy(() -> Currency.valueOf(unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueOfNull_throwsNullPointerException() {
        assertThatThrownBy(() -> Currency.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // name / toString
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void vndName_isVND() {
        assertThat(Currency.VND.name()).isEqualTo("VND");
    }

    @Test
    void usdName_isUSD() {
        assertThat(Currency.USD.name()).isEqualTo("USD");
    }

    @Test
    void vndToString_isVND() {
        assertThat(Currency.VND.toString()).isEqualTo("VND");
    }

    @Test
    void usdToString_isUSD() {
        assertThat(Currency.USD.toString()).isEqualTo("USD");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ordinal — declaration order is part of the public contract for persistence
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void vndOrdinal_isZero() {
        assertThat(Currency.VND.ordinal()).isZero();
    }

    @Test
    void usdOrdinal_isOne() {
        assertThat(Currency.USD.ordinal()).isOne();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Identity / equality
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void sameConstant_isSameInstance() {
        assertThat(Currency.VND).isSameAs(Currency.VND);
        assertThat(Currency.USD).isSameAs(Currency.USD);
    }

    @Test
    void differentConstants_areNotEqual() {
        assertThat(Currency.VND).isNotEqualTo(Currency.USD);
    }
}
