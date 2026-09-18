package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.request.CreateExpenseRequest;
import com.fintrack.planning.dto.response.ExpenseResponse;
import com.fintrack.planning.model.Expense;
import com.fintrack.planning.model.ExpenseCategory;
import com.fintrack.planning.model.PlanEntity;
import com.fintrack.planning.model.enums.ExpenseType;
import com.fintrack.planning.repository.ExpenseCategoryRepository;
import com.fintrack.planning.repository.ExpenseRepository;
import com.fintrack.planning.repository.PlanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExpenseServiceImpl} — create-time validation and type
 * denormalisation, ownership enforcement on linked plans, and date-window
 * resolution before the list query.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_ID = "user-2";
    private static final String CATEGORY_ID = "cat-1";
    private static final String PLAN_ID = "plan-1";

    @Mock private ExpenseRepository expenseRepository;
    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private PlanRepository planRepository;

    @InjectMocks private ExpenseServiceImpl expenseService;

    // ─────────────────────────────────────────────────────────────────────────
    // create
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createExpense_futureDate_isRejected() {
        CreateExpenseRequest request = baseRequest();
        request.setSpentOn(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> expenseService.createExpense(USER_ID, request))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createExpense_categoryNotOwned_isRejected() {
        CreateExpenseRequest request = baseRequest();
        when(expenseCategoryRepository.findById(CATEGORY_ID))
                .thenReturn(Optional.of(category(OTHER_ID, ExpenseType.FIXED)));

        assertThatThrownBy(() -> expenseService.createExpense(USER_ID, request))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXPENSE_CATEGORY_NOT_FOUND);
    }

    @Test
    void createExpense_planOwnedByAnotherUser_isRejected() {
        CreateExpenseRequest request = baseRequest();
        request.setPlanId(PLAN_ID);
        when(expenseCategoryRepository.findById(CATEGORY_ID))
                .thenReturn(Optional.of(category(USER_ID, ExpenseType.VARIABLE)));
        PlanEntity foreignPlan = new PlanEntity();
        foreignPlan.setUserId(OTHER_ID);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(foreignPlan));

        assertThatThrownBy(() -> expenseService.createExpense(USER_ID, request))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXPENSE_PLAN_MISMATCH);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createExpense_copiesCategoryDefaultTypeOntoExpense() {
        CreateExpenseRequest request = baseRequest();
        ExpenseCategory category = category(USER_ID, ExpenseType.FIXED);
        when(expenseCategoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExpenseResponse response = expenseService.createExpense(USER_ID, request);

        ArgumentCaptor<Expense> saved = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(saved.capture());
        assertThat(saved.getValue().getExpenseType()).isEqualTo(ExpenseType.FIXED);
        assertThat(response.getExpenseType()).isEqualTo(ExpenseType.FIXED);

        // The category's defaultType later flipping to VARIABLE must not touch the
        // already-persisted expense — the value was copied, not joined.
        category.setDefaultType(ExpenseType.VARIABLE);
        assertThat(saved.getValue().getExpenseType()).isEqualTo(ExpenseType.FIXED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // list — date window resolution
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void listExpenses_31DayRange_passesResolvedWindowToRepository() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = from.plusDays(30);
        when(expenseRepository.search(eq(USER_ID), eq(from), eq(to), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        expenseService.listExpenses(USER_ID, from, to, null, null, null, 0, 20);

        verify(expenseRepository).search(eq(USER_ID), eq(from), eq(to), isNull(), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void listExpenses_32DayRange_isRejectedBeforeQuery() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = from.plusDays(31);

        assertThatThrownBy(() -> expenseService.listExpenses(USER_ID, from, to, null, null, null, 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DATE_RANGE_TOO_LARGE);

        verify(expenseRepository, never()).search(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listExpenses_onlyFromSupplied_isRejectedBeforeQuery() {
        assertThatThrownBy(() ->
                expenseService.listExpenses(USER_ID, LocalDate.of(2026, 3, 1), null, null, null, null, 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARTIAL_DATE_RANGE);

        verify(expenseRepository, never()).search(any(), any(), any(), any(), any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────────────────

    private CreateExpenseRequest baseRequest() {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setCurrency("USD");
        request.setCategoryId(CATEGORY_ID);
        request.setSpentOn(LocalDate.now().minusDays(1));
        return request;
    }

    private ExpenseCategory category(String ownerId, ExpenseType defaultType) {
        return ExpenseCategory.builder()
                .userId(ownerId)
                .name("Groceries")
                .defaultType(defaultType)
                .system(false)
                .build();
    }
}
