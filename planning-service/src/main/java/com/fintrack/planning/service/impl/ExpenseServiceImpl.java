package com.fintrack.planning.service.impl;

import com.fintrack.core.dto.PageResponse;
import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.DateRange;
import com.fintrack.planning.dto.request.CreateExpenseRequest;
import com.fintrack.planning.dto.request.UpdateExpenseRequest;
import com.fintrack.planning.dto.response.ExpenseResponse;
import com.fintrack.planning.model.Expense;
import com.fintrack.planning.model.ExpenseCategory;
import com.fintrack.planning.model.PlanEntity;
import com.fintrack.planning.model.enums.ExpenseSource;
import com.fintrack.planning.model.enums.ExpenseType;
import com.fintrack.planning.repository.ExpenseCategoryRepository;
import com.fintrack.planning.repository.ExpenseRepository;
import com.fintrack.planning.repository.PlanRepository;
import com.fintrack.planning.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Default implementation of {@link ExpenseService}.
 *
 * <p><b>Deletion:</b> expenses are <em>soft</em>-deleted ({@code deleted = true}),
 * following the codebase-wide {@code BaseEntity} convention. This is deliberately
 * unlike {@code PlanServiceImpl#deletePlan}, whose hard delete is a documented
 * exception — expense rows are financial history and are kept.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final PlanRepository planRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ExpenseResponse createExpense(String userId, CreateExpenseRequest request) {
        rejectFutureDate(request.getSpentOn());

        ExpenseCategory category = getOwnedCategoryOrThrow(userId, request.getCategoryId());

        // TODO: validate request.currency against the user's account currency.
        //       auth-service owns the user's preferred currency; it is not available
        //       here without a cross-service call, and there is no client for that yet.

        if (request.getPlanId() != null) {
            verifyPlanOwnership(userId, request.getPlanId());
        }

        Expense expense = expenseRepository.save(Expense.builder()
                .userId(userId)
                .planId(request.getPlanId())
                .categoryId(category.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .expenseType(category.getDefaultType())
                .spentOn(request.getSpentOn())
                .note(request.getNote())
                .source(ExpenseSource.MANUAL)
                .build());

        log.info("Created expense: id={}, userId={}, categoryId={}", expense.getId(), userId, category.getId());
        return toResponse(expense);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> listExpenses(String userId, LocalDate from, LocalDate to,
                                                      String planId, ExpenseType type, String categoryId,
                                                      int page, int size) {
        DateRange range = DateRange.resolve(from, to);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "spentOn"));

        Page<ExpenseResponse> result = expenseRepository
                .search(userId, range.from(), range.to(), planId, type, categoryId, pageable)
                .map(this::toResponse);

        return PageResponse.of(result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ExpenseResponse updateExpense(String userId, String expenseId, UpdateExpenseRequest request) {
        Expense expense = getOwnedExpenseOrThrow(userId, expenseId);

        if (request.getAmount() != null) {
            expense.setAmount(request.getAmount());
        }
        if (request.getCurrency() != null) {
            expense.setCurrency(request.getCurrency());
        }
        if (request.getCategoryId() != null) {
            ExpenseCategory category = getOwnedCategoryOrThrow(userId, request.getCategoryId());
            expense.setCategoryId(category.getId());
        }
        if (request.getExpenseType() != null) {
            expense.setExpenseType(request.getExpenseType());
        }
        if (request.getSpentOn() != null) {
            rejectFutureDate(request.getSpentOn());
            expense.setSpentOn(request.getSpentOn());
        }
        if (request.getNote() != null) {
            expense.setNote(request.getNote());
        }
        if (request.getPlanId() != null) {
            verifyPlanOwnership(userId, request.getPlanId());
            expense.setPlanId(request.getPlanId());
        }

        expenseRepository.save(expense);
        log.info("Updated expense: id={}, userId={}", expenseId, userId);
        return toResponse(expense);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteExpense(String userId, String expenseId) {
        Expense expense = getOwnedExpenseOrThrow(userId, expenseId);
        expense.setDeleted(true);
        expenseRepository.save(expense);
        log.info("Soft-deleted expense: id={}, userId={}", expenseId, userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void rejectFutureDate(LocalDate spentOn) {
        if (spentOn != null && spentOn.isAfter(LocalDate.now())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Expense date cannot be in the future");
        }
    }

    private ExpenseCategory getOwnedCategoryOrThrow(String userId, String categoryId) {
        ExpenseCategory category = expenseCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
                        "Expense category not found with id: " + categoryId));

        if (category.isDeleted() || !category.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.EXPENSE_CATEGORY_NOT_FOUND,
                    "Expense category not found with id: " + categoryId);
        }
        return category;
    }

    private Expense getOwnedExpenseOrThrow(String userId, String expenseId) {
        Expense expense = expenseRepository.findByIdAndDeletedFalse(expenseId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPENSE_NOT_FOUND,
                        "Expense not found with id: " + expenseId));

        if (!expense.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Expense does not belong to the current user");
        }
        return expense;
    }

    /**
     * Reuses the plan-ownership pattern from {@code PlanServiceImpl}: load by ID,
     * verify {@code plan.userId == userId}. Throws {@link ErrorCode#EXPENSE_PLAN_MISMATCH}
     * on either miss so a caller cannot probe for plan IDs they do not own.
     */
    private void verifyPlanOwnership(String userId, String planId) {
        PlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.EXPENSE_PLAN_MISMATCH,
                        "Linked plan not found with id: " + planId));

        if (!plan.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.EXPENSE_PLAN_MISMATCH,
                    "Linked plan does not belong to the current user");
        }
    }

    private ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .planId(expense.getPlanId())
                .categoryId(expense.getCategoryId())
                .amount(expense.getAmount())
                .currency(expense.getCurrency())
                .expenseType(expense.getExpenseType())
                .spentOn(expense.getSpentOn())
                .note(expense.getNote())
                .source(expense.getSource())
                .build();
    }
}
