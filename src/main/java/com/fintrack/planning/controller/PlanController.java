package com.fintrack.planning.controller;

import com.fintrack.core.dto.ApiResponse;
import com.fintrack.planning.dto.request.CreatePlanRequest;
import com.fintrack.planning.dto.request.ToggleRecalculateRequest;
import com.fintrack.planning.dto.request.UpdateMilestoneRequest;
import com.fintrack.planning.dto.response.PlanResponse;
import com.fintrack.planning.dto.response.PlanSummaryResponse;
import com.fintrack.planning.service.PlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the Financial Planning feature — savings goal creation,
 * milestone tracking, and deficit redistribution.
 *
 * <p>Base path: {@code /api/v1/plans}. Every endpoint requires a valid JWT issued
 * by {@code auth-service}; the authenticated user's ID is injected via
 * {@link AuthenticationPrincipal} and used to enforce per-user ownership.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Plans", description = "Create and track personal savings goals and their milestone schedules")
@SecurityRequirement(name = "******")
public class PlanController {

    private final PlanService planService;

    @Operation(
        summary = "Create a new savings plan",
        description = "Generates the full milestone schedule (one per day/month/year depending on frequency), splitting the target amount evenly with any remainder on the last milestone."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Plan created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreatePlanRequest request) {
        PlanResponse response = planService.createPlan(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "List the current user's plans", description = "Returns lightweight summaries — call the detail endpoint for the full milestone schedule.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plans returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlanSummaryResponse>>> listPlans(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(planService.listPlans(userId)));
    }

    @Operation(
        summary = "Get a plan's full detail",
        description = "Returns the plan, its complete milestone schedule, and totals. Milestone status and target savings are always recomputed live against the current date."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Plan returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Plan belongs to another user"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found")
    })
    @GetMapping("/{planId}")
    public ResponseEntity<ApiResponse<PlanResponse>> getPlan(
            @AuthenticationPrincipal String userId,
            @PathVariable String planId) {
        return ResponseEntity.ok(ApiResponse.success(planService.getPlan(userId, planId)));
    }

    @Operation(summary = "Update a milestone's actual saved amount")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Milestone updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Plan belongs to another user"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan or milestone not found")
    })
    @PatchMapping("/{planId}/milestones/{milestoneId}")
    public ResponseEntity<ApiResponse<PlanResponse>> updateMilestone(
            @AuthenticationPrincipal String userId,
            @PathVariable String planId,
            @PathVariable String milestoneId,
            @Valid @RequestBody UpdateMilestoneRequest request) {
        PlanResponse response = planService.updateMilestoneActualSaved(userId, planId, milestoneId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Milestone updated"));
    }

    @Operation(
        summary = "Mark a milestone as complete",
        description = "Sets actualSaved to the milestone's current effective target savings, unless it is already higher."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Milestone completed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Plan belongs to another user"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan or milestone not found")
    })
    @PostMapping("/{planId}/milestones/{milestoneId}/complete")
    public ResponseEntity<ApiResponse<PlanResponse>> completeMilestone(
            @AuthenticationPrincipal String userId,
            @PathVariable String planId,
            @PathVariable String milestoneId) {
        PlanResponse response = planService.completeMilestone(userId, planId, milestoneId);
        return ResponseEntity.ok(ApiResponse.success(response, "Milestone marked complete"));
    }

    @Operation(
        summary = "Undo a milestone's completion",
        description = "Clears the manual-completion flag; status reverts to Pending or Overdue based on the current date."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Completion undone"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Plan belongs to another user"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan or milestone not found")
    })
    @PostMapping("/{planId}/milestones/{milestoneId}/undo")
    public ResponseEntity<ApiResponse<PlanResponse>> undoMilestone(
            @AuthenticationPrincipal String userId,
            @PathVariable String planId,
            @PathVariable String milestoneId) {
        PlanResponse response = planService.undoMilestone(userId, planId, milestoneId);
        return ResponseEntity.ok(ApiResponse.success(response, "Milestone completion undone"));
    }

    @Operation(summary = "Toggle deficit redistribution", description = "Enables or disables redistributing missed-deadline deficits across future milestones.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Settings updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Plan belongs to another user"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found")
    })
    @PatchMapping("/{planId}/settings")
    public ResponseEntity<ApiResponse<PlanResponse>> updateSettings(
            @AuthenticationPrincipal String userId,
            @PathVariable String planId,
            @Valid @RequestBody ToggleRecalculateRequest request) {
        PlanResponse response = planService.updateSettings(userId, planId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Settings updated"));
    }

    @Operation(summary = "Delete a plan", description = "Soft-deletes the plan and removes its milestones.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Plan deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Plan belongs to another user"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Plan not found")
    })
    @DeleteMapping("/{planId}")
    public ResponseEntity<Void> deletePlan(
            @AuthenticationPrincipal String userId,
            @PathVariable String planId) {
        planService.deletePlan(userId, planId);
        return ResponseEntity.noContent().build();
    }
}
