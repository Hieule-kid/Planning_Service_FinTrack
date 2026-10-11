package com.fintrack.planning.service;

import com.fintrack.core.dto.PageResponse;
import com.fintrack.planning.dto.request.CreateExpenseMultipleRequest;
import com.fintrack.planning.dto.request.CreateExpenseRequest;
import com.fintrack.planning.dto.request.UpdateExpenseRequest;
import com.fintrack.planning.dto.response.CreateExpenseMultipleResponse;
import com.fintrack.planning.dto.response.ExpenseResponse;
import com.fintrack.planning.dto.response.ExpenseSummaryResponse;
import com.fintrack.planning.model.enums.ExpenseType;

import java.time.LocalDate;
import java.util.List;

/**
 * Service contract for recording and querying a user's expenses.
 *
 */
public interface ExpenseService {

    ExpenseResponse createExpense(String userId, CreateExpenseRequest request);

    PageResponse<ExpenseResponse> listExpenses(String userId, LocalDate from, LocalDate to,
                                               String planId, ExpenseType type, String categoryId,
                                               int page, int size);

    /**
     * Totals per currency over every expense matching the same window/type/category filters as
     * {@link #listExpenses}, plus the resolved filter that was applied. {@code totals} is empty when
     * nothing matches.
     */
    ExpenseSummaryResponse getSummary(String userId, LocalDate from, LocalDate to,
                                      ExpenseType type, String categoryId);

    ExpenseResponse updateExpense(String userId, String expenseId, UpdateExpenseRequest request);

    void deleteExpense(String userId, String expenseId);

    CreateExpenseMultipleResponse createExpenseMultiple(String userId, CreateExpenseMultipleRequest request);
}
