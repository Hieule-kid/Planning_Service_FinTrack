package com.fintrack.planning.controller;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.core.exception.GlobalExceptionHandler;
import com.fintrack.planning.config.SecurityConfig;
import com.fintrack.planning.dto.response.PlanExpenseSummaryResponse;
import com.fintrack.planning.service.ExpenseService;
import com.fintrack.planning.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link ExpenseController}'s plan-expense summary endpoint.
 *
 */
@WebMvcTest(controllers = ExpenseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ExpenseControllerTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final String USER_ID     = "user-1";
    private static final String PLAN_ID     = "plan-1";

    @Autowired private MockMvc mockMvc;

    @MockBean private ExpenseService expenseService;
    @MockBean private JwtService jwtService;

    @Test
    void getPlanExpenseSummary_withoutAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/expenses/summary").param("planId", PLAN_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPlanExpenseSummary_withoutPlanId_returns400() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        mockMvc.perform(get("/api/v1/expenses/summary")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPlanExpenseSummary_withValidToken_returns200() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(expenseService.getPlanExpenseSummary(USER_ID, PLAN_ID)).thenReturn(
                PlanExpenseSummaryResponse.builder()
                        .planId(PLAN_ID)
                        .currency("USD")
                        .totalSpent(new BigDecimal("120.00"))
                        .expenseCount(3)
                        .excludedCount(1)
                        .build());

        mockMvc.perform(get("/api/v1/expenses/summary")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("planId", PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpent").value(120.00))
                .andExpect(jsonPath("$.data.excludedCount").value(1));

        verify(expenseService).getPlanExpenseSummary(USER_ID, PLAN_ID);
    }

    @Test
    void getPlanExpenseSummary_planNotOwned_returns403() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(expenseService.getPlanExpenseSummary(USER_ID, PLAN_ID))
                .thenThrow(new AppException(ErrorCode.EXPENSE_PLAN_MISMATCH, "Linked plan not found with id: " + PLAN_ID));

        mockMvc.perform(get("/api/v1/expenses/summary")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .param("planId", PLAN_ID))
                .andExpect(status().isForbidden());
    }
}
