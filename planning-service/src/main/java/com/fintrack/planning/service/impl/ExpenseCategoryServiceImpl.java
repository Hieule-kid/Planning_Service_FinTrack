package com.fintrack.planning.service.impl;

import com.fintrack.core.exception.AppException;
import com.fintrack.core.exception.ErrorCode;
import com.fintrack.planning.dto.request.CreateExpenseCategoryRequest;
import com.fintrack.planning.dto.response.ExpenseCategoryResponse;
import com.fintrack.planning.model.ExpenseCategory;
import com.fintrack.planning.repository.ExpenseCategoryRepository;
import com.fintrack.planning.service.ExpenseCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default implementation of {@link ExpenseCategoryService}.
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {

    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final ExpenseCategorySeeder seeder;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ExpenseCategoryResponse> listCategories(String userId) {
        List<ExpenseCategory> categories = expenseCategoryRepository.findByUserIdAndDeletedFalse(userId);

        if (categories.isEmpty()) {
            log.info("Seeding {} system default expense categories for userId={}",
                    ExpenseCategorySeeder.SYSTEM_DEFAULTS.size(), userId);
            try {
                categories = seeder.seedDefaults(userId);
            } catch (DataIntegrityViolationException e) {
                // A concurrent first request seeded them already; return what it inserted.
                log.info("Default categories already seeded concurrently for userId={}", userId);
                categories = expenseCategoryRepository.findByUserIdAndDeletedFalse(userId);
            }
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

        ExpenseCategory category;
        try {
            category = expenseCategoryRepository.saveAndFlush(ExpenseCategory.builder()
                    .userId(userId)
                    .name(request.getName())
                    .defaultType(request.getDefaultType())
                    .system(false)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Same-name category created concurrently (the pre-check above cannot see it yet).
            throw new AppException(ErrorCode.DUPLICATE_CATEGORY_NAME,
                    "An expense category named '" + request.getName() + "' already exists");
        }

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
}
