package com.fintrack.planning.repository;

import com.fintrack.planning.model.MilestoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link MilestoneEntity}.
 *
 */
@Repository
public interface MilestoneRepository extends JpaRepository<MilestoneEntity, String> {

    List<MilestoneEntity> findByPlanIdOrderBySequenceIndexAsc(String planId);

    Optional<MilestoneEntity> findByIdAndPlanId(String id, String planId);

    void deleteByPlanId(String planId);
}
