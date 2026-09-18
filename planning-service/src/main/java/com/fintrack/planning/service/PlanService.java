package com.fintrack.planning.service;

import com.fintrack.planning.dto.ai.AiPlanResponse;
import com.fintrack.planning.dto.request.CreatePlanRequest;
import com.fintrack.planning.dto.request.ToggleRecalculateRequest;
import com.fintrack.planning.dto.request.UpdateMilestoneRequest;
import com.fintrack.planning.dto.response.PlanResponse;
import com.fintrack.planning.dto.response.PlanSummaryResponse;

import java.util.List;

/**
 * Service contract for the Financial Planning feature — savings goal creation,
 * milestone tracking, and deficit redistribution.
 *
 * <p>Every method is scoped to a single authenticated user; callers must always
 * pass the requesting user's ID so ownership can be enforced.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public interface PlanService {

    /**
     * Creates a new savings plan and generates its full milestone schedule.
     *
     * @param userId  the owning user's ID
     * @param request the validated creation payload
     * @return the created plan, including its generated schedule and totals
     */
    PlanResponse createPlan(String userId, CreatePlanRequest request);

    /**
     * Lists all of the user's plans as lightweight summaries.
     *
     * @param userId the owning user's ID
     * @return the user's plans, most recently created first
     */
    List<PlanSummaryResponse> listPlans(String userId);

    /**
     * Retrieves a single plan with its full, live-recomputed milestone schedule and totals.
     *
     * @param userId the owning user's ID
     * @param planId the plan's ID
     * @return the plan detail
     */
    PlanResponse getPlan(String userId, String planId);

    /**
     * Updates the amount actually saved towards a specific milestone.
     *
     * @param userId      the owning user's ID
     * @param planId      the plan's ID
     * @param milestoneId the milestone's ID
     * @param request     the validated update payload
     * @return the updated plan detail
     */
    PlanResponse updateMilestoneActualSaved(String userId, String planId, String milestoneId,
                                            UpdateMilestoneRequest request);

    /**
     * Marks a milestone as complete. Sets {@code actualSaved} to its effective target
     * savings, unless it is already higher.
     *
     * @param userId      the owning user's ID
     * @param planId      the plan's ID
     * @param milestoneId the milestone's ID
     * @return the updated plan detail
     */
    PlanResponse completeMilestone(String userId, String planId, String milestoneId);

    /**
     * Undoes a milestone's manual completion, reverting its status to pending or
     * overdue based on the current date.
     *
     * @param userId      the owning user's ID
     * @param planId      the plan's ID
     * @param milestoneId the milestone's ID
     * @return the updated plan detail
     */
    PlanResponse undoMilestone(String userId, String planId, String milestoneId);

    /**
     * Toggles a plan's deficit-redistribution setting.
     *
     * @param userId  the owning user's ID
     * @param planId  the plan's ID
     * @param request the validated settings payload
     * @return the updated plan detail
     */
    PlanResponse updateSettings(String userId, String planId, ToggleRecalculateRequest request);

    /**
     * Deletes a plan and its milestones.
     *
     * @param userId the owning user's ID
     * @param planId the plan's ID
     */
    void deletePlan(String userId, String planId);

    /**
     * Sends a free-text financial prompt to Google Gemini and returns a structured
     * budget breakdown.
     *
     * @param userId the requesting user's ID
     * @param prompt the user's natural-language financial description
     * @return the AI-generated budget plan
     */
    AiPlanResponse generateAiPlan(String userId, String prompt);
}
