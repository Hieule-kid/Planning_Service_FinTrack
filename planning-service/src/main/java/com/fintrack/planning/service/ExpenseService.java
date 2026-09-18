package com.fintrack.planning.service;

import com.fintrack.core.dto.PageResponse;
import com.fintrack.planning.dto.request.CreateExpenseRequest;
import com.fintrack.planning.dto.request.UpdateExpenseRequest;
import com.fintrack.planning.dto.response.ExpenseResponse;
import com.fintrack.planning.model.enums.ExpenseType;

import java.time.LocalDate;

/**
 * Service contract for recording and querying a user's expenses.
 *
 * <p>Every method is scoped to a single authenticated user; callers must always
 * pass the requesting user's ID so ownership can be enforced.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
public interface ExpenseService {

    /**
     * Records a new expense. The {@code expenseType} is copied from the chosen
     * category's {@code defaultType}.
     *
     * @param userId  the owning user's ID
     * @param request the validated creation payload
     * @return the created expense
     */
    ExpenseResponse createExpense(String userId, CreateExpenseRequest request);

    /**
     * Lists the user's expenses within a date window, with optional filters.
     *
     * @param userId     the owning user's ID
     * @param from       window start (inclusive); {@code null} defaults with {@code to}
     * @param to         window end (inclusive); {@code null} defaults with {@code from}
     * @param planId     optional plan filter
     * @param type       optional fixed/variable filter
     * @param categoryId optional category filter
     * @param page       zero-based page index
     * @param size       page size
     * @return a page of matching expenses
     */
    PageResponse<ExpenseResponse> listExpenses(String userId, LocalDate from, LocalDate to,
                                               String planId, ExpenseType type, String categoryId,
                                               int page, int size);

    /**
     * Applies a partial update to an existing expense the user owns.
     *
     * @param userId    the owning user's ID
     * @param expenseId the expense's ID
     * @param request   the partial-update payload
     * @return the updated expense
     */
    ExpenseResponse updateExpense(String userId, String expenseId, UpdateExpenseRequest request);

    /**
     * Soft-deletes an expense the user owns.
     *
     * @param userId    the owning user's ID
     * @param expenseId the expense's ID
     */
    void deleteExpense(String userId, String expenseId);
}
