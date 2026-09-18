package com.fintrack.planning.controller;

import com.fintrack.core.dto.ApiResponse;
import com.fintrack.planning.dto.request.CreateExpenseCategoryRequest;
import com.fintrack.planning.dto.response.ExpenseCategoryResponse;
import com.fintrack.planning.service.ExpenseCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for expense categories — the buckets a user files spending under.
 *
 * <p>Base path: {@code /api/v1/expense-categories}. Every endpoint requires a valid
 * JWT issued by {@code auth-service}; the authenticated user's ID is injected via
 * {@link AuthenticationPrincipal}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/expense-categories")
@RequiredArgsConstructor
@Tag(name = "Expense Categories", description = "Manage the categories a user files expenses under")
public class ExpenseCategoryController {

    private final ExpenseCategoryService expenseCategoryService;

    @Operation(
        summary = "List the current user's expense categories",
        description = "On the user's first call, six system default categories are seeded and returned."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Categories returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpenseCategoryResponse>>> listCategories(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(expenseCategoryService.listCategories(userId)));
    }

    @Operation(
        summary = "Create a new expense category",
        description = "Rejects a name the user already uses (case-insensitive) with 409 DUPLICATE_CATEGORY_NAME."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Category created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate category name")
    })
    @PostMapping
    public ResponseEntity<ApiResponse<ExpenseCategoryResponse>> createCategory(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreateExpenseCategoryRequest request) {
        ExpenseCategoryResponse response = expenseCategoryService.createCategory(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }
}
