package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.request.CreateExpenseMultipleRequest;
import com.fintrack.planning.dto.request.CreateExpenseRequest;
import com.fintrack.planning.dto.response.CreateExpenseMultipleResponse;
import com.fintrack.planning.dto.response.ExpenseResponse;
import com.fintrack.planning.model.Expense;
import com.fintrack.planning.model.ExpenseCategory;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        when(expenseRepository.search(eq(USER_ID), eq(from), eq(to), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        expenseService.listExpenses(USER_ID, from, to, null, null, null, 0, 20);

        verify(expenseRepository).search(eq(USER_ID), eq(from), eq(to), isNull(), isNull(), any(Pageable.class));
    }

    @Test
    void listExpenses_32DayRange_isRejectedBeforeQuery() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = from.plusDays(31);

        assertThatThrownBy(() -> expenseService.listExpenses(USER_ID, from, to, null, null, null, 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DATE_RANGE_TOO_LARGE);

        verify(expenseRepository, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listExpenses_onlyFromSupplied_isRejectedBeforeQuery() {
        assertThatThrownBy(() ->
                expenseService.listExpenses(USER_ID, LocalDate.of(2026, 3, 1), null, null, null, null, 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARTIAL_DATE_RANGE);

        verify(expenseRepository, never()).search(any(), any(), any(), any(), any(), any());
    }

    @Test
    void createExpenseMultiple_allRowsValid_savesAllAndReturnsMappedResponses() {
        CreateExpenseMultipleRequest request = multipleRequest(
                rowRequest(CATEGORY_ID, LocalDate.now().minusDays(1)),
                rowRequest(CATEGORY_ID, LocalDate.now().minusDays(2)));
        ExpenseCategory category = categoryWithId(CATEGORY_ID, USER_ID, ExpenseType.VARIABLE);
        when(expenseCategoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(expenseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CreateExpenseMultipleResponse response = expenseService.createExpenseMultiple(USER_ID, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Expense>> saved = ArgumentCaptor.forClass(List.class);
        verify(expenseRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue()).allSatisfy(e -> {
            assertThat(e.getUserId()).isEqualTo(USER_ID);
            assertThat(e.getCategoryId()).isEqualTo(CATEGORY_ID);
            assertThat(e.getExpenseType()).isEqualTo(ExpenseType.VARIABLE);
        });
        assertThat(response.getExpenseResponses()).hasSize(2);
        verify(expenseRepository, never()).save(any());
    }

    @Test
    void createExpenseMultiple_futureDateInOneRow_rejectsWholeBatch() {
        CreateExpenseMultipleRequest request = multipleRequest(
                rowRequest(CATEGORY_ID, LocalDate.now().minusDays(1)),
                rowRequest(CATEGORY_ID, LocalDate.now().plusDays(1)));
        ExpenseCategory category = categoryWithId(CATEGORY_ID, USER_ID, ExpenseType.VARIABLE);
        when(expenseCategoryRepository.findAllById(any())).thenReturn(List.of(category));

        AppException ex = catchThrowableOfType(
                () -> expenseService.createExpenseMultiple(USER_ID, request), AppException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(ex.getMessage()).contains("Row 2:");
        verify(expenseRepository, never()).saveAll(anyList());
    }

    @Test
    void createExpenseMultiple_categoryNotOwnedByUser_isRejectedWithRowNumber() {
        CreateExpenseMultipleRequest request = multipleRequest(
                rowRequest(CATEGORY_ID, LocalDate.now().minusDays(1)));
        ExpenseCategory foreignCategory = categoryWithId(CATEGORY_ID, OTHER_ID, ExpenseType.FIXED);
        when(expenseCategoryRepository.findAllById(any())).thenReturn(List.of(foreignCategory));

        AppException ex = catchThrowableOfType(
                () -> expenseService.createExpenseMultiple(USER_ID, request), AppException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(ex.getMessage()).contains("Row 1:");
        verify(expenseRepository, never()).saveAll(anyList());
    }

    @Test
    void createExpenseMultiple_multipleInvalidRows_joinsAllMessagesInOneException() {
        CreateExpenseMultipleRequest request = multipleRequest(
                rowRequest(CATEGORY_ID, LocalDate.now().plusDays(1)),
                rowRequest(CATEGORY_ID, LocalDate.now().minusDays(1)),
                rowRequest("missing-cat", LocalDate.now().minusDays(1)),
                rowRequest(CATEGORY_ID, LocalDate.now().minusDays(1)));
        ExpenseCategory category = categoryWithId(CATEGORY_ID, USER_ID, ExpenseType.FIXED);
        when(expenseCategoryRepository.findAllById(any())).thenReturn(List.of(category));

        AppException ex = catchThrowableOfType(
                () -> expenseService.createExpenseMultiple(USER_ID, request), AppException.class);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(ex.getMessage()).contains("Row 1:").contains("Row 3:");
        verify(expenseRepository, never()).saveAll(anyList());
    }

    @Test
    void createExpenseMultiple_batchFetchesCategoriesOnce() {
        CreateExpenseMultipleRequest request = multipleRequest(
                rowRequest(CATEGORY_ID, LocalDate.now().minusDays(1)),
                rowRequest(CATEGORY_ID, LocalDate.now().minusDays(2)));
        ExpenseCategory category = categoryWithId(CATEGORY_ID, USER_ID, ExpenseType.FIXED);
        when(expenseCategoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(expenseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        expenseService.createExpenseMultiple(USER_ID, request);

        verify(expenseCategoryRepository, times(1)).findAllById(any());
        verify(expenseCategoryRepository, never()).findById(any());
    }

    @Test
    void createExpenseMultiple_neverQueriesPlanRepository() {
        CreateExpenseMultipleRequest request = multipleRequest(
                rowRequest(CATEGORY_ID, LocalDate.now().minusDays(1)));
        ExpenseCategory category = categoryWithId(CATEGORY_ID, USER_ID, ExpenseType.FIXED);
        when(expenseCategoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(expenseRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        expenseService.createExpenseMultiple(USER_ID, request);

        verify(planRepository, never()).findAllById(any());
    }

    private CreateExpenseRequest rowRequest(String categoryId, LocalDate spentOn) {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setCurrency("USD");
        request.setCategoryId(categoryId);
        request.setSpentOn(spentOn);
        return request;
    }

    private CreateExpenseMultipleRequest multipleRequest(CreateExpenseRequest... rows) {
        return CreateExpenseMultipleRequest.builder().expenses(List.of(rows)).build();
    }

    private ExpenseCategory categoryWithId(String id, String ownerId, ExpenseType defaultType) {
        ExpenseCategory category = category(ownerId, defaultType);
        category.setId(id);
        return category;
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
