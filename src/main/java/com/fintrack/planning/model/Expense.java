package com.fintrack.planning.model;

import com.fintrack.core.base.BaseEntity;
import com.fintrack.planning.model.enums.ExpenseSource;
import com.fintrack.planning.model.enums.ExpenseType;
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
 * JPA entity representing a single recorded expense.
 *
 * <p><b>Soft links to other domains:</b> {@code userId} references the user in
 * {@code auth-service}; {@code planId} optionally references a {@link PlanEntity};
 * {@code categoryId} references an {@link ExpenseCategory}. None are physical FKs.
 *
 * <p><b>Denormalised {@code expenseType}:</b> copied from the category's
 * {@code defaultType} at create time and never joined back live. Editing a
 * category's {@code defaultType} later must NOT change existing expense rows —
 * the only way to change an expense's type afterwards is an explicit update.
 *
 * <p>Table: {@code expenses}. Indexed on {@code (user_id, spent_on)} for the
 * date-windowed list query.
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
    name = "expenses",
    indexes = {
        @Index(name = "idx_expenses_user_spent_on", columnList = "user_id, spent_on")
    }
)
public class Expense extends BaseEntity {

    /**
     * ID of the user who owns this expense.
     * ⚠️ Soft link: VARCHAR UUID referencing User in auth-service (no physical FK).
     */
    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    private String userId;

    /** Optional link to a savings plan this expense is associated with. Nullable. */
    @Column(name = "plan_id", length = 36)
    private String planId;

    /** The expense category this spending is filed under. Required. */
    @Column(name = "category_id", nullable = false, length = 36)
    private String categoryId;

    /** Amount spent. Always positive. */
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** ISO-4217 currency code (3 letters). */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Fixed/variable classification, denormalised from the category's
     * {@code defaultType} at create time. See the class Javadoc.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "expense_type", nullable = false, length = 20)
    private ExpenseType expenseType;

    /** The calendar date the money was spent. */
    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    /** Optional free-text note. */
    @Column(name = "note", length = 255)
    private String note;

    /** How this row entered the system. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ExpenseSource source = ExpenseSource.MANUAL;
}
