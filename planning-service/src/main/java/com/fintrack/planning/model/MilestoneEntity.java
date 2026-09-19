package com.fintrack.planning.model;

import com.fintrack.core.base.BaseEntity;
import com.fintrack.planning.model.enums.MilestoneStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA entity representing a single milestone (interval) within a {@link PlanEntity}'s
 * generated savings schedule.
 *
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id", callSuper = false)
@Entity
@Table(
    name = "milestones",
    indexes = {
        @Index(name = "idx_milestones_plan_id", columnList = "plan_id")
    },
    uniqueConstraints = {
        @jakarta.persistence.UniqueConstraint(name = "uq_milestones_plan_seq", columnNames = {"plan_id", "sequence_index"})
    }
)
public class MilestoneEntity extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(
        name = "plan_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_milestones_plan_id")
    )
    private PlanEntity plan;

    @Column(name = "sequence_index", nullable = false)
    private int sequenceIndex;

    @Column(name = "timeline", nullable = false, length = 50)
    private String timeline;

    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    @Column(name = "base_target_savings", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal baseTargetSavings;

    @Column(name = "target_savings", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetSavings;

    @Builder.Default
    @Column(name = "actual_saved", nullable = false, precision = 19, scale = 2)
    private BigDecimal actualSaved = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "manually_completed", nullable = false)
    private boolean manuallyCompleted = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MilestoneStatus status = MilestoneStatus.PENDING;
}
