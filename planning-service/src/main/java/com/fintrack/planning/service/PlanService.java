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
 */
public interface PlanService {

    PlanResponse createPlan(String userId, CreatePlanRequest request);

    List<PlanSummaryResponse> listPlans(String userId);

    PlanResponse getPlan(String userId, String planId);

    PlanResponse updateMilestoneActualSaved(String userId, String planId, String milestoneId,
                                            UpdateMilestoneRequest request);

    PlanResponse completeMilestone(String userId, String planId, String milestoneId);

    PlanResponse undoMilestone(String userId, String planId, String milestoneId);

    PlanResponse updateSettings(String userId, String planId, ToggleRecalculateRequest request);

    void deletePlan(String userId, String planId);

    AiPlanResponse generateAiPlan(String userId, String prompt);
}
