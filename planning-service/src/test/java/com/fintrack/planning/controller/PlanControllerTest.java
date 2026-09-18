package com.fintrack.planning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.core.exception.GlobalExceptionHandler;
import com.fintrack.planning.config.SecurityConfig;
import com.fintrack.planning.dto.request.CreatePlanRequest;
import com.fintrack.planning.dto.request.ToggleRecalculateRequest;
import com.fintrack.planning.dto.request.UpdateMilestoneRequest;
import com.fintrack.planning.dto.response.PlanResponse;
import com.fintrack.planning.dto.response.PlanSummaryResponse;
import com.fintrack.planning.service.JwtService;
import com.fintrack.planning.service.PlanService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link PlanController} — covers all endpoints plus
 * JWT authentication/rejection behaviour enforced by {@link SecurityConfig}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@WebMvcTest(controllers = PlanController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PlanControllerTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final String USER_ID     = "user-1";
    private static final String PLAN_ID     = "plan-1";
    private static final String MS_ID       = "ms-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PlanService planService;
    @MockBean private JwtService  jwtService;

    // ─────────────────────────────────────────────────────────────────────────
    // Auth rejection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPlan_withoutAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/plans/plan-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPlan_withInvalidToken_returns401() throws Exception {
        when(jwtService.extractUserId("invalid-token")).thenThrow(new JwtException("bad signature"));

        mockMvc.perform(get("/api/v1/plans/plan-1")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listPlans_withoutAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/plans — createPlan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createPlan_withInvalidPayload_returns400() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        CreatePlanRequest request = new CreatePlanRequest(); // all required fields missing

        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/plans — listPlans
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void listPlans_withValidToken_returns200() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.listPlans(USER_ID)).thenReturn(List.of(
                PlanSummaryResponse.builder().id(PLAN_ID).goalTitle("Emergency Fund").build()));

        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());

        verify(planService).listPlans(USER_ID);
    }

    @Test
    void listPlans_withValidToken_returnsEmptyArray() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.listPlans(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/plans/{planId} — getPlan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getPlan_withValidToken_returns200() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.getPlan(USER_ID, PLAN_ID)).thenReturn(
                PlanResponse.builder()
                        .id(PLAN_ID)
                        .goalTitle("Emergency Fund")
                        .milestones(List.of())
                        .totalSaved(BigDecimal.ZERO)
                        .remaining(BigDecimal.ZERO)
                        .progressPercent(BigDecimal.ZERO)
                        .build());

        mockMvc.perform(get("/api/v1/plans/" + PLAN_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());

        verify(planService).getPlan(USER_ID, PLAN_ID);
    }

    @Test
    void getPlan_planNotFound_returns404() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.getPlan(USER_ID, PLAN_ID))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Plan not found"));

        mockMvc.perform(get("/api/v1/plans/" + PLAN_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPlan_planBelongsToOtherUser_returns403() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.getPlan(USER_ID, PLAN_ID))
                .thenThrow(new AppException(ErrorCode.FORBIDDEN, "Plan does not belong to the current user"));

        mockMvc.perform(get("/api/v1/plans/" + PLAN_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/plans/{planId}/milestones/{milestoneId} — updateMilestone
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void updateMilestone_withValidToken_returns200() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.updateMilestoneActualSaved(eq(USER_ID), eq(PLAN_ID), eq(MS_ID), any()))
                .thenReturn(PlanResponse.builder()
                        .id(PLAN_ID)
                        .milestones(List.of())
                        .totalSaved(BigDecimal.ZERO)
                        .remaining(BigDecimal.ZERO)
                        .progressPercent(BigDecimal.ZERO)
                        .build());

        UpdateMilestoneRequest req = new UpdateMilestoneRequest();
        req.setActualSaved(new BigDecimal("75.00"));

        mockMvc.perform(patch("/api/v1/plans/" + PLAN_ID + "/milestones/" + MS_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(planService).updateMilestoneActualSaved(eq(USER_ID), eq(PLAN_ID), eq(MS_ID), any());
    }

    @Test
    void updateMilestone_withMissingBody_returns400() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        UpdateMilestoneRequest req = new UpdateMilestoneRequest(); // actualSaved is null

        mockMvc.perform(patch("/api/v1/plans/" + PLAN_ID + "/milestones/" + MS_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMilestone_milestoneNotFound_returns404() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.updateMilestoneActualSaved(eq(USER_ID), eq(PLAN_ID), eq(MS_ID), any()))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Milestone not found"));

        UpdateMilestoneRequest req = new UpdateMilestoneRequest();
        req.setActualSaved(new BigDecimal("75.00"));

        mockMvc.perform(patch("/api/v1/plans/" + PLAN_ID + "/milestones/" + MS_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/plans/{planId}/milestones/{milestoneId}/complete
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void completeMilestone_withValidToken_returns200() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.completeMilestone(USER_ID, PLAN_ID, MS_ID))
                .thenReturn(PlanResponse.builder()
                        .id(PLAN_ID)
                        .milestones(List.of())
                        .totalSaved(BigDecimal.ZERO)
                        .remaining(BigDecimal.ZERO)
                        .progressPercent(BigDecimal.ZERO)
                        .build());

        mockMvc.perform(post("/api/v1/plans/" + PLAN_ID + "/milestones/" + MS_ID + "/complete")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());

        verify(planService).completeMilestone(USER_ID, PLAN_ID, MS_ID);
    }

    @Test
    void completeMilestone_planNotFound_returns404() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.completeMilestone(USER_ID, PLAN_ID, MS_ID))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Plan not found"));

        mockMvc.perform(post("/api/v1/plans/" + PLAN_ID + "/milestones/" + MS_ID + "/complete")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/plans/{planId}/milestones/{milestoneId}/undo
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void undoMilestone_withValidToken_returns200() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.undoMilestone(USER_ID, PLAN_ID, MS_ID))
                .thenReturn(PlanResponse.builder()
                        .id(PLAN_ID)
                        .milestones(List.of())
                        .totalSaved(BigDecimal.ZERO)
                        .remaining(BigDecimal.ZERO)
                        .progressPercent(BigDecimal.ZERO)
                        .build());

        mockMvc.perform(post("/api/v1/plans/" + PLAN_ID + "/milestones/" + MS_ID + "/undo")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());

        verify(planService).undoMilestone(USER_ID, PLAN_ID, MS_ID);
    }

    @Test
    void undoMilestone_planBelongsToOtherUser_returns403() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.undoMilestone(USER_ID, PLAN_ID, MS_ID))
                .thenThrow(new AppException(ErrorCode.FORBIDDEN, "Access denied"));

        mockMvc.perform(post("/api/v1/plans/" + PLAN_ID + "/milestones/" + MS_ID + "/undo")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /api/v1/plans/{planId}/settings — updateSettings
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void updateSettings_enableRedistribution_returns200() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.updateSettings(eq(USER_ID), eq(PLAN_ID), any()))
                .thenReturn(PlanResponse.builder()
                        .id(PLAN_ID)
                        .milestones(List.of())
                        .totalSaved(BigDecimal.ZERO)
                        .remaining(BigDecimal.ZERO)
                        .progressPercent(BigDecimal.ZERO)
                        .build());

        ToggleRecalculateRequest req = new ToggleRecalculateRequest();
        req.setRecalculateOnMissedDeadline(true);

        mockMvc.perform(patch("/api/v1/plans/" + PLAN_ID + "/settings")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(planService).updateSettings(eq(USER_ID), eq(PLAN_ID), any());
    }

    @Test
    void updateSettings_withMissingField_returns400() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        ToggleRecalculateRequest req = new ToggleRecalculateRequest(); // recalculateOnMissedDeadline is null

        mockMvc.perform(patch("/api/v1/plans/" + PLAN_ID + "/settings")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSettings_planNotFound_returns404() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.updateSettings(eq(USER_ID), eq(PLAN_ID), any()))
                .thenThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Plan not found"));

        ToggleRecalculateRequest req = new ToggleRecalculateRequest();
        req.setRecalculateOnMissedDeadline(true);

        mockMvc.perform(patch("/api/v1/plans/" + PLAN_ID + "/settings")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/plans/{planId} — deletePlan
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void deletePlan_withValidToken_returns204() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        mockMvc.perform(delete("/api/v1/plans/" + PLAN_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(planService).deletePlan(USER_ID, PLAN_ID);
    }

    @Test
    void deletePlan_planNotFound_returns404() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        doThrow(new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Plan not found"))
                .when(planService).deletePlan(USER_ID, PLAN_ID);

        mockMvc.perform(delete("/api/v1/plans/" + PLAN_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletePlan_planBelongsToOtherUser_returns403() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        doThrow(new AppException(ErrorCode.FORBIDDEN, "Access denied"))
                .when(planService).deletePlan(USER_ID, PLAN_ID);

        mockMvc.perform(delete("/api/v1/plans/" + PLAN_ID)
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isForbidden());
    }
}
