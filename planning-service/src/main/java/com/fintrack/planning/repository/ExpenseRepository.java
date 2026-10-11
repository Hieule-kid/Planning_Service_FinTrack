package com.fintrack.planning.repository;

import com.fintrack.planning.model.Expense;
import com.fintrack.planning.model.enums.ExpenseType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
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

    /**
     * The WHERE clause shared by {@link #search} and {@link #summarizeByCurrency} so the list and
     * its totals can never disagree about which expenses match. The CAST on the date parameters is
     * required: Postgres cannot infer the type of a bare {@code ? IS NULL} when the value is null.
     */
    String SEARCH_FILTER = """
            WHERE e.userId = :userId
              AND e.deleted = false
              AND (CAST(:from AS LocalDate) IS NULL OR e.spentOn >= :from)
              AND (CAST(:to AS LocalDate) IS NULL OR e.spentOn <= :to)
              AND (:type IS NULL OR e.expenseType = :type)
              AND (:categoryId IS NULL OR e.categoryId = :categoryId)
            """;

    Optional<Expense> findByIdAndDeletedFalse(String id);

    /**
     * Filtered, paginated search over a user's expenses.
     *
     * <p>Every filter except the owner is optional — a {@code null} argument disables that
     * predicate via the {@code (:param IS NULL OR field = :param)} pattern. {@code from} and
     * {@code to} are inclusive bounds; both {@code null} means all time.
     *
     * @param userId     the owning user's ID
     * @param from       earliest {@code spentOn} (inclusive), or {@code null} for no lower bound
     * @param to         latest {@code spentOn} (inclusive), or {@code null} for no upper bound
     * @param type       optional fixed/variable filter
     * @param categoryId optional category filter
     * @param pageable   paging + sorting
     * @return the matching page of expenses
     */
    @Query("SELECT e FROM Expense e " + SEARCH_FILTER)
    Page<Expense> search(@Param("userId") String userId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("type") ExpenseType type,
                         @Param("categoryId") String categoryId,
                         Pageable pageable);

    /**
     * Totals over ALL expenses matching the same filters as {@link #search}, one row per currency
     * (currencies are never combined). {@code fixed + variable == total} by construction.
     */
    @Query("SELECT e.currency AS currency, "
            + "COUNT(e) AS expenseCount, "
            + "COALESCE(SUM(e.amount), 0) AS total, "
            + "COALESCE(SUM(CASE WHEN e.expenseType = com.fintrack.planning.model.enums.ExpenseType.FIXED "
            + "THEN e.amount ELSE 0 END), 0) AS fixed, "
            + "COALESCE(SUM(CASE WHEN e.expenseType = com.fintrack.planning.model.enums.ExpenseType.VARIABLE "
            + "THEN e.amount ELSE 0 END), 0) AS variable "
            + "FROM Expense e " + SEARCH_FILTER
            + "GROUP BY e.currency ORDER BY e.currency")
    List<ExpenseCurrencyTotals> summarizeByCurrency(@Param("userId") String userId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to,
                                                    @Param("type") ExpenseType type,
                                                    @Param("categoryId") String categoryId);
}
