package com.fintrack.planning.repository;

import com.fintrack.planning.model.MilestoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link MilestoneEntity}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Repository
public interface MilestoneRepository extends JpaRepository<MilestoneEntity, String> {

    /**
     * Returns every milestone belonging to a plan, ordered by their schedule sequence.
     *
     * @param planId the parent plan's ID
     * @return the plan's milestones, in schedule order
     */
    List<MilestoneEntity> findByPlanIdOrderBySequenceIndexAsc(String planId);

    /**
     * Finds a single milestone scoped to a specific plan, so that a milestone cannot
     * be looked up or modified through a plan it does not belong to.
     *
     * @param id     the milestone ID
     * @param planId the parent plan's ID
     * @return the milestone, if found within {@code planId}
     */
    Optional<MilestoneEntity> findByIdAndPlanId(String id, String planId);

    /**
     * Bulk-deletes every milestone belonging to a plan. Used when a plan is deleted.
     *
     * @param planId the parent plan's ID
     */
    void deleteByPlanId(String planId);
}
