package com.fintrack.planning.controller;

import com.fintrack.core.dto.ApiResponse;
import com.fintrack.core.dto.PageResponse;
import com.fintrack.planning.dto.request.CreateExpenseRequest;
import com.fintrack.planning.dto.request.UpdateExpenseRequest;
import com.fintrack.planning.dto.response.ExpenseResponse;
import com.fintrack.planning.model.enums.ExpenseType;
import com.fintrack.planning.service.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * REST controller for recording and querying expenses.
 *
 * <p>Base path: {@code /api/v1/expenses}. Every endpoint requires a valid JWT
 * issued by {@code auth-service}; the authenticated user's ID is injected via
 * {@link AuthenticationPrincipal} and used to enforce per-user ownership.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Tag(name = "Expenses", description = "Record spending and query it over a date window")
public class ExpenseController {

    private final ExpenseService expenseService;

    @Operation(
        summary = "Record a new expense",
        description = "The fixed/variable type is taken from the chosen category's default at creation time."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Expense recorded"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or future date"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Linked plan belongs to another user"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseResponse>> createExpense(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateExpenseRequest request) {
        ExpenseResponse response = expenseService.createExpense(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(
        summary = "List expenses in a date window",
        description = "Defaults to the last 31 days when no dates are given. The window may not exceed 31 days; "
                + "supply both 'from' and 'to' or neither."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Expenses returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid or oversized date range"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ExpenseResponse>>> listExpenses(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String planId,
            @RequestParam(required = false) ExpenseType type,
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ExpenseResponse> result =
                expenseService.listExpenses(userId, from, to, planId, type, categoryId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Partially update an expense", description = "PATCH semantics — only the supplied fields change. The only place to override the fixed/variable type.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Expense updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or future date"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Expense or linked plan belongs to another user"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense or category not found")
    })
    @PatchMapping("/{expenseId}")
    public ResponseEntity<ApiResponse<ExpenseResponse>> updateExpense(
            @AuthenticationPrincipal String userId,
            @PathVariable String expenseId,
            @Valid @RequestBody UpdateExpenseRequest request) {
        ExpenseResponse response = expenseService.updateExpense(userId, expenseId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Expense updated"));
    }

    @Operation(summary = "Delete an expense", description = "Soft delete — the row is retained as financial history but excluded from all reads.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Expense deleted"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Expense belongs to another user"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense not found")
    })
    @DeleteMapping("/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @AuthenticationPrincipal String userId,
            @PathVariable String expenseId) {
        expenseService.deleteExpense(userId, expenseId);
        return ResponseEntity.noContent().build();
    }
}
