package com.fintrack.planning.model;

import com.fintrack.core.base.BaseEntity;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.TimeframeCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
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
 * JPA entity representing a single financial savings goal ("plan").
 *
 * <p>A plan owns a full schedule of {@link MilestoneEntity} rows generated at
 * creation time — one per interval (day/month/year) depending on {@link #frequency}.
 *
 * <p><b>Soft link to auth-service:</b> {@code userId} is a VARCHAR UUID referencing
 * the user in {@code auth-service}. There is no physical foreign key across services;
 * every query MUST filter by {@code userId} to enforce multi-tenancy.
 *
 * <p>Table: {@code plans}
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
    name = "plans",
    indexes = {
        @Index(name = "idx_plans_user_id", columnList = "user_id")
    }
)
public class PlanEntity extends BaseEntity {

    /**
     * ID of the user who owns this plan.
     * ⚠️ Soft link: VARCHAR UUID referencing User in auth-service (no physical FK).
     */
    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    private String userId;

    /** Human-readable name of the savings goal (e.g. "Emergency Fund"). */
    @Column(name = "goal_title", nullable = false, length = 50)
    private String goalTitle;

    /** Total amount the user wants to save. Always positive. */
    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    /** Whether this is a short-term (3-12 months) or long-term (1-30 years) goal. */
    @Enumerated(EnumType.STRING)
    @Column(name = "timeframe_category", nullable = false, length = 20)
    private TimeframeCategory timeframeCategory;

    /** Duration in months — populated only when {@link #timeframeCategory} is {@code SHORT_TERM}. */
    @Column(name = "duration_in_months")
    private Integer durationInMonths;

    /** Duration in years — populated only when {@link #timeframeCategory} is {@code LONG_TERM}. */
    @Column(name = "duration_in_years")
    private Integer durationInYears;

    /** How often milestones recur (daily/monthly/annually). */
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private Frequency frequency;

    /** The amount the user committed to saving per interval when creating the plan. */
    @Column(name = "required_per_period", nullable = false, precision = 19, scale = 2)
    private BigDecimal requiredPerPeriod;

    /** The date the plan (and its first milestone interval) starts. */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * When {@code true}, deficits from missed (overdue) milestones are redistributed
     * evenly across the remaining future, not-yet-completed milestones every time
     * the plan is read. When {@code false}, the original schedule is left untouched.
     */
    @Builder.Default
    @Column(name = "recalculate_on_missed_deadline", nullable = false)
    private boolean recalculateOnMissedDeadline = false;
}
