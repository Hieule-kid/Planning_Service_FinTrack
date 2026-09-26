package com.fintrack.planning.repository;

import com.fintrack.planning.model.Expense;
import com.fintrack.planning.model.enums.ExpenseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * JPA repository for {@link Expense}.
 *
 * <p>{@code BaseEntity}'s soft-delete field is named {@code deleted}
 * (column {@code is_deleted}); all queries here filter {@code deleted = false}.
 *
 */
@Repository
public interface ExpenseRepository extends JpaRepository<Expense, String> {

    Optional<Expense> findByIdAndDeletedFalse(String id);

    /**
     * Filtered, paginated search over a user's expenses.
     *
     * <p>Every filter except the date window is optional — a {@code null} argument
     * disables that predicate via the {@code (:param IS NULL OR field = :param)}
     * pattern. {@code spentOn} is always constrained to the inclusive
     * {@code [from, to]} window (Postgres {@code BETWEEN} is inclusive).
     *
     * @param userId     the owning user's ID
     * @param from       window start (inclusive)
     * @param to         window end (inclusive)
     * @param planId     optional plan filter
     * @param type       optional fixed/variable filter
     * @param categoryId optional category filter
     * @param pageable   paging + sorting
     * @return the matching page of expenses
     */
    @Query("""
            SELECT e FROM Expense e
            WHERE e.userId = :userId
              AND e.deleted = false
              AND e.spentOn BETWEEN :from AND :to
              AND (:planId IS NULL OR e.planId = :planId)
              AND (:type IS NULL OR e.expenseType = :type)
              AND (:categoryId IS NULL OR e.categoryId = :categoryId)
            """)
    Page<Expense> search(@Param("userId") String userId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("planId") String planId,
                         @Param("type") ExpenseType type,
                         @Param("categoryId") String categoryId,
                         Pageable pageable);

    /**
     * Total number of non-deleted expenses linked to a plan, regardless of currency.
     */
    @Query("""
            SELECT COUNT(e) FROM Expense e
            WHERE e.userId = :userId AND e.planId = :planId AND e.deleted = false
            """)
    long countByUserIdAndPlanId(@Param("userId") String userId, @Param("planId") String planId);

    /**
     * Number of non-deleted expenses linked to a plan whose currency matches the given one.
     *
     * <p>Used alongside {@link #sumAmountByUserIdAndPlanIdAndCurrency} — a plan's spend total is
     * only meaningful when every summed expense shares the plan's currency, so callers compare
     * this against {@link #countByUserIdAndPlanId} to report how many rows were excluded rather
     * than silently mixing currencies into one sum.
     */
    @Query("""
            SELECT COUNT(e) FROM Expense e
            WHERE e.userId = :userId AND e.planId = :planId AND e.deleted = false AND e.currency = :currency
            """)
    long countByUserIdAndPlanIdAndCurrency(@Param("userId") String userId,
                                           @Param("planId") String planId,
                                           @Param("currency") String currency);

    /**
     * Sum of non-deleted expenses linked to a plan whose currency matches the given one.
     * Returns {@link BigDecimal#ZERO} rather than {@code null} when there are none.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amount), 0) FROM Expense e
            WHERE e.userId = :userId AND e.planId = :planId AND e.deleted = false AND e.currency = :currency
            """)
    BigDecimal sumAmountByUserIdAndPlanIdAndCurrency(@Param("userId") String userId,
                                                      @Param("planId") String planId,
                                                      @Param("currency") String currency);
}
