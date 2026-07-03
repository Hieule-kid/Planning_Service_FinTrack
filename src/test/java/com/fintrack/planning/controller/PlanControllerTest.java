package com.fintrack.planning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.core.exception.GlobalExceptionHandler;
import com.fintrack.planning.config.SecurityConfig;
import com.fintrack.planning.dto.request.CreatePlanRequest;
import com.fintrack.planning.dto.response.PlanResponse;
import com.fintrack.planning.dto.response.PlanSummaryResponse;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.TimeframeCategory;
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
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link PlanController} — covers the main endpoints plus
 * JWT authentication/rejection behaviour enforced by {@link SecurityConfig}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@WebMvcTest(controllers = PlanController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PlanControllerTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final String USER_ID = "user-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlanService planService;

    @MockBean
    private JwtService jwtService;

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

    // ─────────────────────────────────────────────────────────────────────────
    // Happy paths
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createPlan_withValidTokenAndPayload_returns201() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        PlanResponse response = PlanResponse.builder()
                .id("plan-1")
                .goalTitle("Emergency Fund")
                .targetAmount(new BigDecimal("1200.00"))
                .milestones(List.of())
                .totalSaved(BigDecimal.ZERO)
                .remaining(new BigDecimal("1200.00"))
                .progressPercent(BigDecimal.ZERO)
                .build();
        when(planService.createPlan(eq(USER_ID), any(CreatePlanRequest.class))).thenReturn(response);

        CreatePlanRequest request = new CreatePlanRequest();
        request.setGoalTitle("Emergency Fund");
        request.setTargetAmount(new BigDecimal("1200.00"));
        request.setTimeframeCategory(TimeframeCategory.SHORT_TERM);
        request.setDurationInMonths(6);
        request.setFrequency(Frequency.MONTHLY);
        request.setRequiredPerPeriod(new BigDecimal("200.00"));
        request.setStartDate(LocalDate.of(2027, 1, 1));

        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void createPlan_withInvalidPayload_returns400() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        // Missing required fields (goalTitle, targetAmount, etc.)
        CreatePlanRequest request = new CreatePlanRequest();

        mockMvc.perform(post("/api/v1/plans")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listPlans_withValidToken_returns200() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(planService.listPlans(USER_ID)).thenReturn(List.of(
                PlanSummaryResponse.builder().id("plan-1").goalTitle("Emergency Fund").build()));

        mockMvc.perform(get("/api/v1/plans")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk());

        verify(planService).listPlans(USER_ID);
    }

    @Test
    void deletePlan_withValidToken_returns204() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        mockMvc.perform(delete("/api/v1/plans/plan-1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isNoContent());

        verify(planService).deletePlan(USER_ID, "plan-1");
    }
}
