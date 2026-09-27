package com.fintrack.planning.service.impl;

import com.fintrack.core.dto.PageResponse;
import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.DateRange;
import com.fintrack.planning.dto.request.CreateExpenseMultipleRequest;
import com.fintrack.planning.dto.request.CreateExpenseRequest;
import com.fintrack.planning.dto.request.UpdateExpenseRequest;
import com.fintrack.planning.dto.response.CreateExpenseMultipleResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Default implementation of {@link ExpenseService}.
 *
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

        Expense expense = expenseRepository.save(buildExpense(userId, request, category));

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
                .search(userId, range.from(), range.to(), type, categoryId, pageable)
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

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public CreateExpenseMultipleResponse createExpenseMultiple(String userId, CreateExpenseMultipleRequest request) {
        List<CreateExpenseRequest> rows = request.getExpenses();
        Map<String, ExpenseCategory> categoriesById = findCategoriesByIdIn(rows);

        List<String> errors = collectRowErrors(userId, rows, categoriesById);
        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, String.join("; ", errors));
        }

        List<Expense> toSave = rows.stream()
                .map(row -> buildExpense(userId, row, categoriesById.get(row.getCategoryId())))
                .toList();
        List<Expense> saved = expenseRepository.saveAll(toSave);
        log.info("Bulk-created {} expenses for userId={}", saved.size(), userId);

        return CreateExpenseMultipleResponse.builder()
                .expenseResponses(saved.stream().map(this::toResponse).toList())
                .build();
    }

    private Map<String, ExpenseCategory> findCategoriesByIdIn(List<CreateExpenseRequest> rows) {
        Set<String> categoryIds = rows.stream()
                .map(CreateExpenseRequest::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return expenseCategoryRepository.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, Function.identity()));
    }

    /**
     * Validates every row up front so a batch failure reports every offending row at once,
     * rather than stopping at the first one.
     */
    private List<String> collectRowErrors(String userId, List<CreateExpenseRequest> rows,
                                           Map<String, ExpenseCategory> categoriesById) {
        return IntStream.range(0, rows.size())
                .mapToObj(i -> validateRow(userId, rows.get(i), categoriesById)
                        .map(error -> "Row " + (i + 1) + ": " + error))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * @return the row's failure reason, or empty if the row is valid.
     */
    private Optional<String> validateRow(String userId, CreateExpenseRequest row,
                                          Map<String, ExpenseCategory> categoriesById) {
        if (row.getSpentOn() != null && row.getSpentOn().isAfter(LocalDate.now())) {
            return Optional.of("Expense date cannot be in the future");
        }

        ExpenseCategory category = categoriesById.get(row.getCategoryId());
        if (category == null || category.isDeleted() || !category.getUserId().equals(userId)) {
            return Optional.of("Expense category not found with id: " + row.getCategoryId());
        }

        return Optional.empty();
    }

    private Expense buildExpense(String userId, CreateExpenseRequest row, ExpenseCategory category) {
        return Expense.builder()
                .userId(userId)
                .categoryId(category.getId())
                .amount(row.getAmount())
                .currency(row.getCurrency())
                .expenseType(category.getDefaultType())
                .spentOn(row.getSpentOn())
                .note(row.getNote())
                .source(ExpenseSource.MANUAL)
                .build();
    }

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
