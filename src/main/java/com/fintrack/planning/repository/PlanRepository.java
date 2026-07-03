package com.fintrack.planning.repository;

import com.fintrack.planning.model.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link PlanEntity}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Repository
public interface PlanRepository extends JpaRepository<PlanEntity, String> {

    /**
     * Returns all non-deleted plans owned by the given user, most recently created first.
     *
     * @param userId the owning user's ID
     * @return the user's plans
     */
    List<PlanEntity> findByUserIdAndDeletedFalseOrderByCreatedAtDesc(String userId);

    /**
     * Finds a single non-deleted plan by ID and owning user, enforcing multi-tenancy
     * directly in the query.
     *
     * @param id     the plan ID
     * @param userId the owning user's ID
     * @return the plan, if found and owned by {@code userId}
     */
    Optional<PlanEntity> findByIdAndUserIdAndDeletedFalse(String id, String userId);
}
