package com.fintrack.planning.repository;

import com.fintrack.planning.model.PlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link PlanEntity}.
 *
 */
@Repository
public interface PlanRepository extends JpaRepository<PlanEntity, String> {

    List<PlanEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<PlanEntity> findByIdAndUserId(String id, String userId);
}
