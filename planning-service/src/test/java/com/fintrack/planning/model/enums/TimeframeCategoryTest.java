package com.fintrack.planning.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TimeframeCategory}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
class TimeframeCategoryTest {

    @Test
    void shouldContainExactlyThreeValues() {
        assertThat(TimeframeCategory.values()).hasSize(3);
    }

    @Test
    void shouldContainShortTerm() {
        assertThat(TimeframeCategory.values()).contains(TimeframeCategory.SHORT_TERM);
    }

    @Test
    void shouldContainMidTerm() {
        assertThat(TimeframeCategory.values()).contains(TimeframeCategory.MID_TERM);
    }

    @Test
    void shouldContainLongTerm() {
        assertThat(TimeframeCategory.values()).contains(TimeframeCategory.LONG_TERM);
    }

    @Test
    void valueOfShortTerm_returnsShortTermConstant() {
        assertThat(TimeframeCategory.valueOf("SHORT_TERM")).isEqualTo(TimeframeCategory.SHORT_TERM);
    }

    @Test
    void valueOfMidTerm_returnsMidTermConstant() {
        assertThat(TimeframeCategory.valueOf("MID_TERM")).isEqualTo(TimeframeCategory.MID_TERM);
    }

    @Test
    void valueOfLongTerm_returnsLongTermConstant() {
        assertThat(TimeframeCategory.valueOf("LONG_TERM")).isEqualTo(TimeframeCategory.LONG_TERM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"short_term", "mid_term", "long_term", "SHORT", "MEDIUM", "LONG", ""})
    void valueOfUnknownName_throwsIllegalArgumentException(String unknown) {
        assertThatThrownBy(() -> TimeframeCategory.valueOf(unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueOfNull_throwsNullPointerException() {
        assertThatThrownBy(() -> TimeframeCategory.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shortTermOrdinal_isZero() {
        assertThat(TimeframeCategory.SHORT_TERM.ordinal()).isZero();
    }

    @Test
    void midTermOrdinal_isOne() {
        assertThat(TimeframeCategory.MID_TERM.ordinal()).isOne();
    }

    @Test
    void longTermOrdinal_isTwo() {
        assertThat(TimeframeCategory.LONG_TERM.ordinal()).isEqualTo(2);
    }

    @Test
    void names_matchExpectedStrings() {
        assertThat(TimeframeCategory.SHORT_TERM.name()).isEqualTo("SHORT_TERM");
        assertThat(TimeframeCategory.MID_TERM.name()).isEqualTo("MID_TERM");
        assertThat(TimeframeCategory.LONG_TERM.name()).isEqualTo("LONG_TERM");
    }

    @Test
    void sameConstant_isSameInstance() {
        assertThat(TimeframeCategory.SHORT_TERM).isSameAs(TimeframeCategory.SHORT_TERM);
        assertThat(TimeframeCategory.MID_TERM).isSameAs(TimeframeCategory.MID_TERM);
        assertThat(TimeframeCategory.LONG_TERM).isSameAs(TimeframeCategory.LONG_TERM);
    }

    @Test
    void differentConstants_areNotEqual() {
        assertThat(TimeframeCategory.SHORT_TERM).isNotEqualTo(TimeframeCategory.MID_TERM);
        assertThat(TimeframeCategory.SHORT_TERM).isNotEqualTo(TimeframeCategory.LONG_TERM);
        assertThat(TimeframeCategory.MID_TERM).isNotEqualTo(TimeframeCategory.LONG_TERM);
    }
}
