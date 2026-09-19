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
 */
@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, String> {

    List<ExpenseCategory> findByUserIdAndDeletedFalse(String userId);

    boolean existsByUserIdAndNameIgnoreCaseAndDeletedFalse(String userId, String name);
}
