package com.fintrack.planning.model;

import com.fintrack.core.base.BaseEntity;
import com.fintrack.planning.model.enums.ExpenseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing an expense category — a bucket a user files spending
 * under (e.g. "Rent", "Groceries").
 *
 * <p><b>Soft link to auth-service:</b> {@code userId} is a VARCHAR UUID referencing
 * the user in {@code auth-service}. There is no physical foreign key across services;
 * every query MUST filter by {@code userId} to enforce multi-tenancy.
 *
 * <p><b>System defaults:</b> the first time a user reads their categories, a fixed
 * set of six categories is seeded with {@code system = true}. User-created categories
 * have {@code system = false}. The flag exists so the two can be told apart in the UI.
 *
 * <p>Table: {@code expense_categories}. Unique on {@code (user_id, name)}.
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
    name = "expense_categories",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_expense_categories_user_name", columnNames = {"user_id", "name"})
    },
    indexes = {
        @Index(name = "idx_expense_categories_user_id", columnList = "user_id")
    }
)
public class ExpenseCategory extends BaseEntity {

    /**
     * ID of the user who owns this category.
     * ⚠️ Soft link: VARCHAR UUID referencing User in auth-service (no physical FK).
     */
    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    private String userId;

    /** Display name of the category (e.g. "Groceries"). Unique per user. */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * Default classification applied to new expenses filed under this category.
     * Copied onto each {@code Expense} at create time, not joined back live.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_type", nullable = false, length = 20)
    private ExpenseType defaultType;

    /**
     * {@code true} for the six categories seeded automatically on first read,
     * {@code false} for categories the user created themselves.
     */
    @Builder.Default
    @Column(name = "is_system", nullable = false)
    private boolean system = false;
}
