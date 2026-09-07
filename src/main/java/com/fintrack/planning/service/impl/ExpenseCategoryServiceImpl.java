package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.request.CreateExpenseCategoryRequest;
import com.fintrack.planning.dto.response.ExpenseCategoryResponse;
import com.fintrack.planning.model.ExpenseCategory;
import com.fintrack.planning.model.enums.ExpenseType;
import com.fintrack.planning.repository.ExpenseCategoryRepository;
import com.fintrack.planning.service.ExpenseCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default implementation of {@link ExpenseCategoryService}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {

    /**
     * The categories seeded the first time a user reads their category list.
     * Order is preserved in the response.
     */
    private static final List<SeedCategory> SYSTEM_DEFAULTS = List.of(
            new SeedCategory("Rent", ExpenseType.FIXED),
            new SeedCategory("Utilities", ExpenseType.FIXED),
            new SeedCategory("Subscriptions", ExpenseType.FIXED),
            new SeedCategory("Groceries", ExpenseType.VARIABLE),
            new SeedCategory("Transport", ExpenseType.VARIABLE),
            new SeedCategory("Dining out", ExpenseType.VARIABLE)
    );

    private final ExpenseCategoryRepository expenseCategoryRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public List<ExpenseCategoryResponse> listCategories(String userId) {
        List<ExpenseCategory> categories = expenseCategoryRepository.findByUserIdAndDeletedFalse(userId);

        if (categories.isEmpty()) {
            log.info("Seeding {} system default expense categories for userId={}", SYSTEM_DEFAULTS.size(), userId);
            List<ExpenseCategory> seeded = SYSTEM_DEFAULTS.stream()
                    .map(seed -> ExpenseCategory.builder()
                            .userId(userId)
                            .name(seed.name())
                            .defaultType(seed.defaultType())
                            .system(true)
                            .build())
                    .toList();
            categories = expenseCategoryRepository.saveAll(seeded);
        }

        return categories.stream().map(this::toResponse).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ExpenseCategoryResponse createCategory(String userId, CreateExpenseCategoryRequest request) {
        if (expenseCategoryRepository.existsByUserIdAndNameIgnoreCaseAndDeletedFalse(userId, request.getName())) {
            throw new AppException(ErrorCode.DUPLICATE_CATEGORY_NAME,
                    "An expense category named '" + request.getName() + "' already exists");
        }

        ExpenseCategory category = expenseCategoryRepository.save(ExpenseCategory.builder()
                .userId(userId)
                .name(request.getName())
                .defaultType(request.getDefaultType())
                .system(false)
                .build());

        log.info("Created expense category: id={}, userId={}", category.getId(), userId);
        return toResponse(category);
    }

    private ExpenseCategoryResponse toResponse(ExpenseCategory category) {
        return ExpenseCategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .defaultType(category.getDefaultType())
                .system(category.isSystem())
                .build();
    }

    /** Immutable description of one system-default category. */
    private record SeedCategory(String name, ExpenseType defaultType) {
    }
}
