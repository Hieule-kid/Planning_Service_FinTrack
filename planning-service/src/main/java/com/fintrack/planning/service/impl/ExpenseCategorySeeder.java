package com.fintrack.planning.service.impl;

import com.fintrack.planning.model.ExpenseCategory;
import com.fintrack.planning.model.enums.ExpenseType;
import com.fintrack.planning.repository.ExpenseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Inserts a user's system-default expense categories in a transaction of its own.
 *
 * <p>Isolated so that when two first-time requests race, the loser's unique-constraint failure
 * ({@code uk_expense_categories_user_name}) rolls back only this transaction and surfaces as a
 * {@link org.springframework.dao.DataIntegrityViolationException} the caller can recover from by
 * re-reading what the winner inserted.
 */
@Component
@RequiredArgsConstructor
class ExpenseCategorySeeder {

    static final List<SeedCategory> SYSTEM_DEFAULTS = List.of(
            new SeedCategory("Rent", ExpenseType.FIXED),
            new SeedCategory("Utilities", ExpenseType.FIXED),
            new SeedCategory("Subscriptions", ExpenseType.FIXED),
            new SeedCategory("Groceries", ExpenseType.VARIABLE),
            new SeedCategory("Transport", ExpenseType.VARIABLE),
            new SeedCategory("Dining out", ExpenseType.VARIABLE)
    );

    private final ExpenseCategoryRepository expenseCategoryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ExpenseCategory> seedDefaults(String userId) {
        List<ExpenseCategory> seeded = SYSTEM_DEFAULTS.stream()
                .map(seed -> ExpenseCategory.builder()
                        .userId(userId)
                        .name(seed.name())
                        .defaultType(seed.defaultType())
                        .system(true)
                        .build())
                .toList();
        List<ExpenseCategory> saved = expenseCategoryRepository.saveAll(seeded);
        expenseCategoryRepository.flush(); // surface the constraint violation here, not at commit
        return saved;
    }

    record SeedCategory(String name, ExpenseType defaultType) {
    }
}
