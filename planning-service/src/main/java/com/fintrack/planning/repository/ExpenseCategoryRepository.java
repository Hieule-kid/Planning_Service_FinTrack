package com.fintrack.planning.repository;

import com.fintrack.planning.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA repository for {@link ExpenseCategory}.
 *
 * <p>Note: {@code BaseEntity}'s soft-delete field is named {@code deleted}
 * (column {@code is_deleted}), so the derived queries use {@code ...AndDeletedFalse}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, String> {

    /**
     * Returns all non-deleted categories owned by the given user.
     *
     * @param userId the owning user's ID
     * @return the user's categories
     */
    List<ExpenseCategory> findByUserIdAndDeletedFalse(String userId);

    /**
     * Checks whether the user already has a non-deleted category with this name,
     * case-insensitively ("Groceries" collides with "groceries").
     *
     * @param userId the owning user's ID
     * @param name   the candidate category name
     * @return {@code true} if a matching category already exists
     */
    boolean existsByUserIdAndNameIgnoreCaseAndDeletedFalse(String userId, String name);
}
