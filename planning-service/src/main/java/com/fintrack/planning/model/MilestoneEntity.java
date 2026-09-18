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
 * <p><b>Live fields:</b> {@link #status} and the effective {@code targetSavings} shown
 * to clients are always recomputed at read time against the current date — see
 * {@code MilestoneCalculator} in the service layer. The persisted {@link #status} column
 * is a best-effort cache only and must never be trusted as the source of truth.
 *
 * <p>Table: {@code milestones}
 *
 * @author FinTrack Team
 * @since 1.0.0
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

    /**
     * The parent plan this milestone belongs to.
     * Physical foreign key — enforces referential integrity at the database level.
     */
    @ManyToOne(optional = false)
    @JoinColumn(
        name = "plan_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_milestones_plan_id")
    )
    private PlanEntity plan;

    /** Zero-based position of this milestone within the plan's schedule, used for ordering. */
    @Column(name = "sequence_index", nullable = false)
    private int sequenceIndex;

    /** Human-readable timeline label, e.g. {@code "Day 1"}, {@code "January 2027"}, {@code "Year 2 (2028)"}. */
    @Column(name = "timeline", nullable = false, length = 50)
    private String timeline;

    /** The calendar date this interval represents (start of the day/month/year). */
    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    /** The date by which this milestone's savings should have been achieved. */
    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    /** The original, immutable allocation computed when the plan was created. */
    @Column(name = "base_target_savings", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal baseTargetSavings;

    /**
     * The allocation as last computed — mirrors {@link #baseTargetSavings} at creation time.
     * The effective, current value (including any live deficit redistribution) is always
     * recomputed at read time and must not be read directly for business decisions.
     */
    @Column(name = "target_savings", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetSavings;

    /** Amount actually saved towards this milestone so far. */
    @Builder.Default
    @Column(name = "actual_saved", nullable = false, precision = 19, scale = 2)
    private BigDecimal actualSaved = BigDecimal.ZERO;

    /**
     * Manual completion flag — set when the user explicitly marks the milestone complete
     * via {@code POST /complete}, independent of whether {@code actualSaved} has caught up.
     */
    @Builder.Default
    @Column(name = "manually_completed", nullable = false)
    private boolean manuallyCompleted = false;

    /**
     * Cached status — a best-effort snapshot only. Always recompute the live status
     * from {@link #actualSaved}, the effective target, {@link #manuallyCompleted}, and
     * the current date before returning data to clients.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MilestoneStatus status = MilestoneStatus.PENDING;
}
