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
     * Returns all plans owned by the given user, most recently created first.
     *
     * @param userId the owning user's ID
     * @return the user's plans
     */
    List<PlanEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Finds a single plan by ID and owning user, enforcing multi-tenancy
     * directly in the query.
     *
     * @param id     the plan ID
     * @param userId the owning user's ID
     * @return the plan, if found and owned by {@code userId}
     */
    Optional<PlanEntity> findByIdAndUserId(String id, String userId);
}
