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

    @Column(name = "user_id", nullable = false, length = 36, updatable = false)
    private String userId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_type", nullable = false, length = 20)
    private ExpenseType defaultType;

    @Builder.Default
    @Column(name = "is_system", nullable = false)
    private boolean system = false;
}
