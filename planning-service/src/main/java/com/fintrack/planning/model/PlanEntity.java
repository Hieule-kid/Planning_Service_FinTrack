package com.fintrack.planning.model;

import com.fintrack.core.base.BaseEntity;
import com.fintrack.planning.model.enums.Currency;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.PlanCategory;
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
 * creation time.
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
    name = "plans",
    indexes = {
        @Index(name = "idx_plans_user_id", columnList = "user_id")
    }
)
public class PlanEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    private String userId;

    @Column(name = "goal_title", nullable = false, length = 50)
    private String goalTitle;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_category", length = 20)
    private PlanCategory planCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "timeframe_category", nullable = false, length = 20)
    private TimeframeCategory timeframeCategory;

    @Column(name = "duration_in_months")
    private Integer durationInMonths;

    @Column(name = "duration_in_years")
    private Integer durationInYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private Frequency frequency;

    @Column(name = "required_per_period", nullable = false, precision = 19, scale = 2)
    private BigDecimal requiredPerPeriod;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Builder.Default
    @Column(name = "recalculate_on_missed_deadline", nullable = false)
    private boolean recalculateOnMissedDeadline = false;
}
