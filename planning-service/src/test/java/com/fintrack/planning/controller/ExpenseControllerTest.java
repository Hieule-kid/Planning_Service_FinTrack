package com.fintrack.planning.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.core.exception.GlobalExceptionHandler;
import com.fintrack.planning.config.SecurityConfig;
import com.fintrack.planning.dto.request.CreateExpenseMultipleRequest;
import com.fintrack.planning.dto.request.CreateExpenseRequest;
import com.fintrack.planning.dto.response.CreateExpenseMultipleResponse;
import com.fintrack.planning.dto.response.ExpenseResponse;
import com.fintrack.planning.model.enums.ExpenseSource;
import com.fintrack.planning.model.enums.ExpenseType;
import com.fintrack.planning.service.ExpenseService;
import com.fintrack.planning.service.JwtService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests for {@link ExpenseController}.
 *
 */
@WebMvcTest(controllers = ExpenseController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ExpenseControllerTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final String USER_ID     = "user-1";
    private static final String CATEGORY_ID = "cat-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ExpenseService expenseService;
    @MockBean private JwtService jwtService;

    @Test
    void createExpense_withValidToken_returns201WithWrappedBody() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(expenseService.createExpense(eq(USER_ID), any())).thenReturn(expenseResponse("exp-1"));

        mockMvc.perform(post("/api/v1/expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRow())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("exp-1"));
    }

    @Test
    void createExpense_withoutAuthorizationHeader_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRow())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createExpense_withInvalidBody_returns400() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        CreateExpenseRequest request = new CreateExpenseRequest();

        mockMvc.perform(post("/api/v1/expenses")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createExpenseMultiple_withValidToken_returns201WithWrappedEnvelope() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(expenseService.createExpenseMultiple(eq(USER_ID), any())).thenReturn(
                CreateExpenseMultipleResponse.builder()
                        .expenseResponses(List.of(expenseResponse("exp-1"), expenseResponse("exp-2")))
                        .build());

        CreateExpenseMultipleRequest request = CreateExpenseMultipleRequest.builder()
                .expenses(List.of(validRow(), validRow()))
                .build();

        mockMvc.perform(post("/api/v1/expenses/multiple")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.expenseResponses.length()").value(2));
    }

    @Test
    void createExpenseMultiple_withoutAuthorizationHeader_returns401() throws Exception {
        CreateExpenseMultipleRequest request = CreateExpenseMultipleRequest.builder()
                .expenses(List.of(validRow()))
                .build();

        mockMvc.perform(post("/api/v1/expenses/multiple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createExpenseMultiple_emptyExpensesList_returns400() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        CreateExpenseMultipleRequest request = CreateExpenseMultipleRequest.builder()
                .expenses(List.of())
                .build();

        mockMvc.perform(post("/api/v1/expenses/multiple")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createExpenseMultiple_moreThan50Rows_returns400() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        CreateExpenseMultipleRequest request = CreateExpenseMultipleRequest.builder()
                .expenses(java.util.stream.Stream.generate(this::validRow).limit(51).toList())
                .build();

        mockMvc.perform(post("/api/v1/expenses/multiple")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createExpenseMultiple_serviceThrowsValidationError_returns400WithJoinedMessage() throws Exception {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(expenseService.createExpenseMultiple(eq(USER_ID), any()))
                .thenThrow(new AppException(ErrorCode.VALIDATION_ERROR,
                        "Row 1: Expense date cannot be in the future; Row 2: Expense category not found with id: bad-cat"));

        CreateExpenseMultipleRequest request = CreateExpenseMultipleRequest.builder()
                .expenses(List.of(validRow(), validRow()))
                .build();

        mockMvc.perform(post("/api/v1/expenses/multiple")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Row 1: Expense date cannot be in the future; Row 2: Expense category not found with id: bad-cat"));
    }

    private CreateExpenseRequest validRow() {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setCurrency("USD");
        request.setCategoryId(CATEGORY_ID);
        request.setSpentOn(LocalDate.now().minusDays(1));
        return request;
    }

    private ExpenseResponse expenseResponse(String id) {
        return ExpenseResponse.builder()
                .id(id)
                .categoryId(CATEGORY_ID)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .expenseType(ExpenseType.VARIABLE)
                .spentOn(LocalDate.now().minusDays(1))
                .source(ExpenseSource.MANUAL)
                .build();
    }
}
