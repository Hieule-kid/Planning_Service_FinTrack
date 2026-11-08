package com.fintrack.planning.repository;

import com.fintrack.planning.model.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link PlanEntity}.
 *
 */
@Repository
public interface PlanRepository extends JpaRepository<PlanEntity, String> {

    List<PlanEntity> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(String userId);

    Optional<PlanEntity> findByIdAndUserIdAndDeletedFalse(String id, String userId);

    /**
     * Per-currency totals across ALL of the user's plans, computed in the database.
     *
     * <p>A plan's saved amount is the sum of {@code actual_saved} over ALL its milestones — partial
     * savings count — matching {@code MilestoneCalculator.computeTotals}. It depends only on stored
     * columns, never on the current date or on deficit redistribution.
     *
     * <p>{@code remaining} clamps per plan (like the per-plan card) before summing, so an
     * over-saved plan cannot offset another plan's shortfall.
     *
     * <p>Native because HQL cannot express the LATERAL join, which lets Postgres use
     * {@code idx_milestones_plan_id} per plan instead of scanning every user's milestones.
     * Soft-deleted rows are excluded explicitly because the entities carry no {@code @SQLRestriction}.
     */
    @Query(value = """
            SELECT p.currency                                                                   AS "currency",
                   COUNT(*)                                                                     AS "planCount",
                   COALESCE(SUM(CASE WHEN COALESCE(s.saved, 0) >= p.target_amount THEN 1 ELSE 0 END), 0)
                                                                                                AS "completedPlanCount",
                   COALESCE(SUM(p.target_amount), 0)                                            AS "totalTarget",
                   COALESCE(SUM(COALESCE(s.saved, 0)), 0)                                       AS "totalSaved",
                   COALESCE(SUM(GREATEST(p.target_amount - COALESCE(s.saved, 0), 0)), 0)        AS "remaining"
            FROM plans p
            LEFT JOIN LATERAL (
                SELECT SUM(m.actual_saved) AS saved
                FROM milestones m
                WHERE m.plan_id = p.id
                  AND m.is_deleted = false
            ) s ON true
            WHERE p.user_id = :userId
              AND p.is_deleted = false
            GROUP BY p.currency
            ORDER BY p.currency
            """, nativeQuery = true)
    List<CurrencyTotals> summarizeByUser(@Param("userId") String userId);
}
