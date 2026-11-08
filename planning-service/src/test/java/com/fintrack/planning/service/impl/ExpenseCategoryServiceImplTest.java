package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.request.CreateExpenseCategoryRequest;
import com.fintrack.planning.dto.response.ExpenseCategoryResponse;
import com.fintrack.planning.model.ExpenseCategory;
import com.fintrack.planning.model.enums.ExpenseType;
import com.fintrack.planning.repository.ExpenseCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseCategoryServiceImplTest {

    private static final String USER_ID = "user-1";

    @Mock private ExpenseCategoryRepository expenseCategoryRepository;
    @Mock private ExpenseCategorySeeder seeder;

    @InjectMocks private ExpenseCategoryServiceImpl service;

    private static ExpenseCategory category(String name, ExpenseType type) {
        return ExpenseCategory.builder().userId(USER_ID).name(name).defaultType(type).system(true).build();
    }

    @Test
    void listCategories_firstCall_seedsDefaults() {
        when(expenseCategoryRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(List.of());
        when(seeder.seedDefaults(USER_ID)).thenReturn(List.of(category("Rent", ExpenseType.FIXED)));

        List<ExpenseCategoryResponse> result = service.listCategories(USER_ID);

        assertThat(result).extracting(ExpenseCategoryResponse::getName).containsExactly("Rent");
    }

    @Test
    void listCategories_lostSeedingRace_returnsWhatTheConcurrentRequestInserted() {
        when(expenseCategoryRepository.findByUserIdAndDeletedFalse(USER_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(category("Rent", ExpenseType.FIXED), category("Groceries", ExpenseType.VARIABLE)));
        when(seeder.seedDefaults(USER_ID)).thenThrow(new DataIntegrityViolationException("uk_expense_categories_user_name"));

        List<ExpenseCategoryResponse> result = service.listCategories(USER_ID);

        assertThat(result).hasSize(2);
        verify(expenseCategoryRepository, times(2)).findByUserIdAndDeletedFalse(USER_ID);
    }

    @Test
    void listCategories_existingCategories_neverSeeds() {
        when(expenseCategoryRepository.findByUserIdAndDeletedFalse(USER_ID))
                .thenReturn(List.of(category("Rent", ExpenseType.FIXED)));

        service.listCategories(USER_ID);

        verify(seeder, never()).seedDefaults(any());
    }

    @Test
    void createCategory_concurrentDuplicateOnSave_isReportedAsDuplicateName() {
        CreateExpenseCategoryRequest request = new CreateExpenseCategoryRequest();
        request.setName("Coffee");
        request.setDefaultType(ExpenseType.VARIABLE);
        when(expenseCategoryRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk"));

        assertThatThrownBy(() -> service.createCategory(USER_ID, request))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_CATEGORY_NAME);
    }
}
