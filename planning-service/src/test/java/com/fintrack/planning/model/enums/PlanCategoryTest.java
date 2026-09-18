package com.fintrack.planning.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PlanCategory}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
class PlanCategoryTest {

    @Test
    void shouldContainExactlyEightValues() {
        assertThat(PlanCategory.values()).hasSize(8);
    }

    @ParameterizedTest
    @EnumSource(PlanCategory.class)
    void allConstants_areNonNull(PlanCategory category) {
        assertThat(category).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "EMERGENCY", "TRAVEL", "HOUSE", "CAR",
        "EDUCATION", "WEDDING", "RETIREMENT", "OTHER"
    })
    void valueOf_returnsCorrectConstantForAllNames(String name) {
        assertThat(PlanCategory.valueOf(name)).isNotNull();
        assertThat(PlanCategory.valueOf(name).name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"emergency", "travel", "VACATION", "SAVINGS", ""})
    void valueOfUnknownName_throwsIllegalArgumentException(String unknown) {
        assertThatThrownBy(() -> PlanCategory.valueOf(unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueOfNull_throwsNullPointerException() {
        assertThatThrownBy(() -> PlanCategory.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emergency_isFirst() {
        assertThat(PlanCategory.EMERGENCY.ordinal()).isZero();
    }

    @Test
    void other_isLast() {
        assertThat(PlanCategory.OTHER.ordinal()).isEqualTo(PlanCategory.values().length - 1);
    }

    @Test
    void sameConstant_isSameInstance() {
        for (PlanCategory cat : PlanCategory.values()) {
            assertThat(PlanCategory.valueOf(cat.name())).isSameAs(cat);
        }
    }

    @Test
    void names_matchExpectedUppercaseStrings() {
        assertThat(PlanCategory.EMERGENCY.name()).isEqualTo("EMERGENCY");
        assertThat(PlanCategory.TRAVEL.name()).isEqualTo("TRAVEL");
        assertThat(PlanCategory.HOUSE.name()).isEqualTo("HOUSE");
        assertThat(PlanCategory.CAR.name()).isEqualTo("CAR");
        assertThat(PlanCategory.EDUCATION.name()).isEqualTo("EDUCATION");
        assertThat(PlanCategory.WEDDING.name()).isEqualTo("WEDDING");
        assertThat(PlanCategory.RETIREMENT.name()).isEqualTo("RETIREMENT");
        assertThat(PlanCategory.OTHER.name()).isEqualTo("OTHER");
    }
}
