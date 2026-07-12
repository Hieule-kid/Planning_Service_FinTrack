package com.fintrack.planning.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MilestoneStatus}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
class MilestoneStatusTest {

    @Test
    void shouldContainExactlyThreeValues() {
        assertThat(MilestoneStatus.values()).hasSize(3);
    }

    @Test
    void shouldContainPending() {
        assertThat(MilestoneStatus.values()).contains(MilestoneStatus.PENDING);
    }

    @Test
    void shouldContainCompleted() {
        assertThat(MilestoneStatus.values()).contains(MilestoneStatus.COMPLETED);
    }

    @Test
    void shouldContainOverdue() {
        assertThat(MilestoneStatus.values()).contains(MilestoneStatus.OVERDUE);
    }

    @Test
    void valueOfPending_returnsPendingConstant() {
        assertThat(MilestoneStatus.valueOf("PENDING")).isEqualTo(MilestoneStatus.PENDING);
    }

    @Test
    void valueOfCompleted_returnsCompletedConstant() {
        assertThat(MilestoneStatus.valueOf("COMPLETED")).isEqualTo(MilestoneStatus.COMPLETED);
    }

    @Test
    void valueOfOverdue_returnsOverdueConstant() {
        assertThat(MilestoneStatus.valueOf("OVERDUE")).isEqualTo(MilestoneStatus.OVERDUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pending", "completed", "overdue", "DONE", "IN_PROGRESS", ""})
    void valueOfUnknownName_throwsIllegalArgumentException(String unknown) {
        assertThatThrownBy(() -> MilestoneStatus.valueOf(unknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueOfNull_throwsNullPointerException() {
        assertThatThrownBy(() -> MilestoneStatus.valueOf(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void pendingOrdinal_isZero() {
        assertThat(MilestoneStatus.PENDING.ordinal()).isZero();
    }

    @Test
    void completedOrdinal_isOne() {
        assertThat(MilestoneStatus.COMPLETED.ordinal()).isOne();
    }

    @Test
    void overdueOrdinal_isTwo() {
        assertThat(MilestoneStatus.OVERDUE.ordinal()).isEqualTo(2);
    }

    @Test
    void names_matchExpectedStrings() {
        assertThat(MilestoneStatus.PENDING.name()).isEqualTo("PENDING");
        assertThat(MilestoneStatus.COMPLETED.name()).isEqualTo("COMPLETED");
        assertThat(MilestoneStatus.OVERDUE.name()).isEqualTo("OVERDUE");
    }

    @Test
    void sameConstant_isSameInstance() {
        assertThat(MilestoneStatus.PENDING).isSameAs(MilestoneStatus.PENDING);
        assertThat(MilestoneStatus.COMPLETED).isSameAs(MilestoneStatus.COMPLETED);
        assertThat(MilestoneStatus.OVERDUE).isSameAs(MilestoneStatus.OVERDUE);
    }

    @Test
    void differentConstants_areNotEqual() {
        assertThat(MilestoneStatus.PENDING).isNotEqualTo(MilestoneStatus.COMPLETED);
        assertThat(MilestoneStatus.PENDING).isNotEqualTo(MilestoneStatus.OVERDUE);
        assertThat(MilestoneStatus.COMPLETED).isNotEqualTo(MilestoneStatus.OVERDUE);
    }
}
