package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.request.CreatePlanRequest;
import com.fintrack.planning.dto.request.ToggleRecalculateRequest;
import com.fintrack.planning.dto.request.UpdateMilestoneRequest;
import com.fintrack.planning.dto.response.MilestoneResponse;
import com.fintrack.planning.dto.response.PlanResponse;
import com.fintrack.planning.dto.response.PlanSummaryResponse;
import com.fintrack.planning.model.MilestoneEntity;
import com.fintrack.planning.model.PlanEntity;
import com.fintrack.planning.repository.MilestoneRepository;
import com.fintrack.planning.repository.PlanRepository;
import com.fintrack.planning.service.PlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Default implementation of {@link PlanService}.
 *
 * <p>All milestone-schedule math (generation, live status, deficit redistribution,
 * totals) is delegated to the stateless {@link MilestoneCalculator} so it can be
 * unit tested without a Spring context.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final MilestoneRepository milestoneRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public PlanResponse createPlan(String userId, CreatePlanRequest request) {
        PlanEntity plan = PlanEntity.builder()
                .userId(userId)
                .goalTitle(request.getGoalTitle())
                .targetAmount(request.getTargetAmount())
                .timeframeCategory(request.getTimeframeCategory())
                .durationInMonths(request.getDurationInMonths())
                .durationInYears(request.getDurationInYears())
                .frequency(request.getFrequency())
                .requiredPerPeriod(request.getRequiredPerPeriod())
                .startDate(request.getStartDate())
                .recalculateOnMissedDeadline(false)
                .build();

        PlanEntity savedPlan = planRepository.save(plan);

        // Generate the full milestone schedule up front — pure math, no persistence side effects.
        List<MilestoneEntity> schedule = MilestoneCalculator.generateSchedule(request);
        schedule.forEach(milestone -> milestone.setPlan(savedPlan));
        List<MilestoneEntity> savedMilestones = milestoneRepository.saveAll(schedule);

        log.info("Created plan: id={}, userId={}, milestoneCount={}",
                savedPlan.getId(), userId, savedMilestones.size());

        return toPlanResponse(savedPlan, savedMilestones);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PlanSummaryResponse> listPlans(String userId) {
        return planRepository.findByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId).stream()
                .map(plan -> {
                    List<MilestoneEntity> milestones = milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(plan.getId());
                    List<MilestoneCalculator.LiveMilestone> live = MilestoneCalculator.computeLiveMilestones(
                            milestones, plan.isRecalculateOnMissedDeadline(), LocalDate.now());
                    MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, plan.getTargetAmount());
                    return toSummaryResponse(plan, totals);
                })
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlanResponse getPlan(String userId, String planId) {
        PlanEntity plan = getOwnedPlanOrThrow(userId, planId);
        List<MilestoneEntity> milestones = milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(planId);
        return toPlanResponse(plan, milestones);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE — milestones
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public PlanResponse updateMilestoneActualSaved(String userId, String planId, String milestoneId,
                                                    UpdateMilestoneRequest request) {
        PlanEntity plan = getOwnedPlanOrThrow(userId, planId);
        MilestoneEntity milestone = getOwnedMilestoneOrThrow(planId, milestoneId);

        milestone.setActualSaved(request.getActualSaved());
        milestoneRepository.save(milestone);

        log.debug("Updated milestone actualSaved: planId={}, milestoneId={}, actualSaved={}",
                planId, milestoneId, request.getActualSaved());

        List<MilestoneEntity> milestones = milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(planId);
        return toPlanResponse(plan, milestones);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlanResponse completeMilestone(String userId, String planId, String milestoneId) {
        PlanEntity plan = getOwnedPlanOrThrow(userId, planId);
        MilestoneEntity milestone = getOwnedMilestoneOrThrow(planId, milestoneId);

        List<MilestoneEntity> milestones = milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(planId);
        BigDecimal effectiveTarget = liveTargetFor(milestone, milestones, plan);

        if (milestone.getActualSaved().compareTo(effectiveTarget) < 0) {
            milestone.setActualSaved(effectiveTarget);
        }
        milestone.setManuallyCompleted(true);
        milestoneRepository.save(milestone);

        log.info("Completed milestone: planId={}, milestoneId={}", planId, milestoneId);

        return toPlanResponse(plan, milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(planId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlanResponse undoMilestone(String userId, String planId, String milestoneId) {
        PlanEntity plan = getOwnedPlanOrThrow(userId, planId);
        MilestoneEntity milestone = getOwnedMilestoneOrThrow(planId, milestoneId);

        milestone.setManuallyCompleted(false);
        milestoneRepository.save(milestone);

        log.info("Undid milestone completion: planId={}, milestoneId={}", planId, milestoneId);

        return toPlanResponse(plan, milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(planId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UPDATE — plan settings
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     */
    @Override
    public PlanResponse updateSettings(String userId, String planId, ToggleRecalculateRequest request) {
        PlanEntity plan = getOwnedPlanOrThrow(userId, planId);
        plan.setRecalculateOnMissedDeadline(request.getRecalculateOnMissedDeadline());
        planRepository.save(plan);

        log.info("Updated plan settings: planId={}, recalculateOnMissedDeadline={}",
                planId, request.getRecalculateOnMissedDeadline());

        return toPlanResponse(plan, milestoneRepository.findByPlanIdOrderBySequenceIndexAsc(planId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * <p>Milestones are physically removed (they are owned exclusively by the plan);
     * the plan itself is soft-deleted to preserve the audit trail.
     */
    @Override
    public void deletePlan(String userId, String planId) {
        PlanEntity plan = getOwnedPlanOrThrow(userId, planId);

        milestoneRepository.deleteByPlanId(planId);
        plan.setDeleted(true);
        planRepository.save(plan);

        log.info("Deleted plan: planId={}, userId={}", planId, userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads a plan by ID and verifies it is owned by {@code userId}.
     *
     * @param userId the requesting user's ID
     * @param planId the plan's ID
     * @return the owned plan entity
     * @throws AppException with {@link ErrorCode#RESOURCE_NOT_FOUND} if no such plan exists
     * @throws AppException with {@link ErrorCode#FORBIDDEN} if the plan belongs to another user
     */
    private PlanEntity getOwnedPlanOrThrow(String userId, String planId) {
        PlanEntity plan = planRepository.findById(planId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Plan not found with id: " + planId));

        if (!plan.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Plan does not belong to the current user");
        }
        return plan;
    }

    /**
     * Loads a milestone by ID, scoped to the given plan.
     *
     * @param planId      the parent plan's ID
     * @param milestoneId the milestone's ID
     * @return the milestone entity
     * @throws AppException with {@link ErrorCode#RESOURCE_NOT_FOUND} if not found within the plan
     */
    private MilestoneEntity getOwnedMilestoneOrThrow(String planId, String milestoneId) {
        return milestoneRepository.findByIdAndPlanId(milestoneId, planId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Milestone not found with id: " + milestoneId));
    }

    /**
     * Resolves the current live effective target savings for one milestone within its plan's
     * full schedule (accounting for deficit redistribution, if enabled).
     *
     * @param milestone  the milestone to resolve
     * @param milestones the full schedule the milestone belongs to
     * @param plan       the parent plan (for the redistribution setting)
     * @return the live effective target savings for {@code milestone}
     */
    private BigDecimal liveTargetFor(MilestoneEntity milestone, List<MilestoneEntity> milestones, PlanEntity plan) {
        return MilestoneCalculator.computeLiveMilestones(milestones, plan.isRecalculateOnMissedDeadline(), LocalDate.now())
                .stream()
                .filter(live -> live.milestone().getId().equals(milestone.getId()))
                .findFirst()
                .map(MilestoneCalculator.LiveMilestone::targetSavings)
                .orElse(milestone.getBaseTargetSavings());
    }

    /**
     * Maps a plan and its milestones to a full {@link PlanResponse}, including live-recomputed
     * milestone statuses/targets and rolled-up totals.
     *
     * @param plan       the plan entity
     * @param milestones the plan's milestones
     * @return the assembled response DTO
     */
    private PlanResponse toPlanResponse(PlanEntity plan, List<MilestoneEntity> milestones) {
        List<MilestoneCalculator.LiveMilestone> live = MilestoneCalculator.computeLiveMilestones(
                milestones, plan.isRecalculateOnMissedDeadline(), LocalDate.now());
        MilestoneCalculator.PlanTotals totals = MilestoneCalculator.computeTotals(live, plan.getTargetAmount());

        List<MilestoneResponse> milestoneResponses = live.stream()
                .map(this::toMilestoneResponse)
                .toList();

        return PlanResponse.builder()
                .id(plan.getId())
                .goalTitle(plan.getGoalTitle())
                .targetAmount(plan.getTargetAmount())
                .timeframeCategory(plan.getTimeframeCategory())
                .durationInMonths(plan.getDurationInMonths())
                .durationInYears(plan.getDurationInYears())
                .frequency(plan.getFrequency())
                .requiredPerPeriod(plan.getRequiredPerPeriod())
                .startDate(plan.getStartDate())
                .recalculateOnMissedDeadline(plan.isRecalculateOnMissedDeadline())
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .milestones(milestoneResponses)
                .totalSaved(totals.totalSaved())
                .remaining(totals.remaining())
                .progressPercent(totals.progressPercent())
                .build();
    }

    /**
     * Maps a plan and pre-computed totals to a lightweight {@link PlanSummaryResponse}.
     *
     * @param plan   the plan entity
     * @param totals the pre-computed live totals
     * @return the assembled summary DTO
     */
    private PlanSummaryResponse toSummaryResponse(PlanEntity plan, MilestoneCalculator.PlanTotals totals) {
        return PlanSummaryResponse.builder()
                .id(plan.getId())
                .goalTitle(plan.getGoalTitle())
                .targetAmount(plan.getTargetAmount())
                .timeframeCategory(plan.getTimeframeCategory())
                .frequency(plan.getFrequency())
                .startDate(plan.getStartDate())
                .recalculateOnMissedDeadline(plan.isRecalculateOnMissedDeadline())
                .totalSaved(totals.totalSaved())
                .remaining(totals.remaining())
                .progressPercent(totals.progressPercent())
                .build();
    }

    /**
     * Maps a live-computed milestone to its response DTO.
     *
     * @param live the live milestone (entity + effective target + derived status)
     * @return the assembled response DTO
     */
    private MilestoneResponse toMilestoneResponse(MilestoneCalculator.LiveMilestone live) {
        MilestoneEntity m = live.milestone();
        return MilestoneResponse.builder()
                .id(m.getId())
                .planId(m.getPlan().getId())
                .sequenceIndex(m.getSequenceIndex())
                .timeline(m.getTimeline())
                .periodDate(m.getPeriodDate())
                .deadline(m.getDeadline())
                .baseTargetSavings(m.getBaseTargetSavings())
                .targetSavings(live.targetSavings())
                .actualSaved(m.getActualSaved())
                .status(live.status())
                .build();
    }
}
