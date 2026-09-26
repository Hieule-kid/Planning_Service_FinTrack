package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.request.CreateExpenseRequest;
import com.fintrack.planning.dto.request.UpdateExpenseRequest;
import com.fintrack.planning.dto.response.ExpenseResponse;
import com.fintrack.planning.dto.response.PlanExpenseSummaryResponse;
import com.fintrack.planning.model.Expense;
import com.fintrack.planning.model.ExpenseCategory;
import com.fintrack.planning.model.PlanEntity;
import com.fintrack.planning.model.enums.Currency;
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
 * Unit tests for {@link ExpenseServiceImpl}.
 *
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

        category.setDefaultType(ExpenseType.VARIABLE);
        assertThat(saved.getValue().getExpenseType()).isEqualTo(ExpenseType.FIXED);
    }

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

    @Test
    void updateExpense_unlinkPlan_clearsExistingAssociation() {
        Expense expense = existingExpense(PLAN_ID);
        when(expenseRepository.findByIdAndDeletedFalse("exp-1")).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setUnlinkPlan(true);

        ExpenseResponse response = expenseService.updateExpense(USER_ID, "exp-1", request);

        assertThat(response.getPlanId()).isNull();
        verify(planRepository, never()).findById(any());
    }

    @Test
    void updateExpense_planIdOmitted_leavesExistingAssociationUnchanged() {
        Expense expense = existingExpense(PLAN_ID);
        when(expenseRepository.findByIdAndDeletedFalse("exp-1")).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setNote("Updated note");

        ExpenseResponse response = expenseService.updateExpense(USER_ID, "exp-1", request);

        assertThat(response.getPlanId()).isEqualTo(PLAN_ID);
    }

    @Test
    void updateExpense_newPlanOwnedByAnotherUser_isRejected() {
        Expense expense = existingExpense(null);
        when(expenseRepository.findByIdAndDeletedFalse("exp-1")).thenReturn(Optional.of(expense));
        PlanEntity foreignPlan = new PlanEntity();
        foreignPlan.setUserId(OTHER_ID);
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(foreignPlan));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setPlanId(PLAN_ID);

        assertThatThrownBy(() -> expenseService.updateExpense(USER_ID, "exp-1", request))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXPENSE_PLAN_MISMATCH);

        verify(expenseRepository, never()).save(any());
    }

    @Test
    void getPlanExpenseSummary_planNotFound_isRejected() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseService.getPlanExpenseSummary(USER_ID, PLAN_ID))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXPENSE_PLAN_MISMATCH);

        verify(expenseRepository, never()).sumAmountByUserIdAndPlanIdAndCurrency(any(), any(), any());
    }

    @Test
    void getPlanExpenseSummary_planOwnedByAnotherUser_isRejected() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan(OTHER_ID, Currency.USD)));

        assertThatThrownBy(() -> expenseService.getPlanExpenseSummary(USER_ID, PLAN_ID))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXPENSE_PLAN_MISMATCH);
    }

    @Test
    void getPlanExpenseSummary_excludesExpensesInADifferentCurrencyFromTheTotal() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan(USER_ID, Currency.USD)));
        when(expenseRepository.countByUserIdAndPlanId(USER_ID, PLAN_ID)).thenReturn(5L);
        when(expenseRepository.countByUserIdAndPlanIdAndCurrency(USER_ID, PLAN_ID, "USD")).thenReturn(3L);
        when(expenseRepository.sumAmountByUserIdAndPlanIdAndCurrency(USER_ID, PLAN_ID, "USD"))
                .thenReturn(new BigDecimal("120.00"));

        PlanExpenseSummaryResponse summary = expenseService.getPlanExpenseSummary(USER_ID, PLAN_ID);

        assertThat(summary.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(summary.getCurrency()).isEqualTo("USD");
        assertThat(summary.getTotalSpent()).isEqualByComparingTo("120.00");
        assertThat(summary.getExpenseCount()).isEqualTo(3L);
        assertThat(summary.getExcludedCount()).isEqualTo(2L);
    }

    @Test
    void getPlanExpenseSummary_noLinkedExpenses_returnsZeroTotals() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(plan(USER_ID, Currency.VND)));
        when(expenseRepository.countByUserIdAndPlanId(USER_ID, PLAN_ID)).thenReturn(0L);
        when(expenseRepository.countByUserIdAndPlanIdAndCurrency(USER_ID, PLAN_ID, "VND")).thenReturn(0L);
        when(expenseRepository.sumAmountByUserIdAndPlanIdAndCurrency(USER_ID, PLAN_ID, "VND"))
                .thenReturn(BigDecimal.ZERO);

        PlanExpenseSummaryResponse summary = expenseService.getPlanExpenseSummary(USER_ID, PLAN_ID);

        assertThat(summary.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getExpenseCount()).isZero();
        assertThat(summary.getExcludedCount()).isZero();
    }

    private Expense existingExpense(String planId) {
        return Expense.builder()
                .userId(USER_ID)
                .planId(planId)
                .categoryId(CATEGORY_ID)
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .expenseType(ExpenseType.VARIABLE)
                .spentOn(LocalDate.now().minusDays(1))
                .build();
    }

    private PlanEntity plan(String ownerId, Currency currency) {
        PlanEntity plan = new PlanEntity();
        plan.setUserId(ownerId);
        plan.setCurrency(currency);
        return plan;
    }

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
