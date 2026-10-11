package com.fintrack.planning.repository;

import com.fintrack.planning.config.JpaAuditingConfig;
import com.fintrack.planning.model.Expense;
import com.fintrack.planning.model.enums.ExpenseType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs {@link ExpenseRepository#summarizeByCurrency} against a real PostgreSQL and checks it agrees
 * with {@link ExpenseRepository#search} on the same filters. Skipped when Docker is not available.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ExpenseRepositorySummaryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String USER = "user-1";
    private static final String OTHER = "user-2";
    private static final LocalDate FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate TO = LocalDate.of(2026, 3, 31);

    @Autowired private ExpenseRepository repository;

    @Test
    void noExpenses_returnsEmpty() {
        assertThat(repository.summarizeByCurrency(USER, FROM, TO, null, null)).isEmpty();
    }

    @Test
    void splitsSpentIntoFixedAndVariable_perCurrency_neverCombined() {
        save(USER, "VND", "5000000", ExpenseType.FIXED, FROM, "rent");
        save(USER, "VND", "250000", ExpenseType.VARIABLE, FROM.plusDays(1), "food");
        save(USER, "VND", "150000", ExpenseType.VARIABLE, FROM.plusDays(2), "food");
        save(USER, "USD", "40.50", ExpenseType.VARIABLE, FROM.plusDays(3), "coffee");

        List<ExpenseCurrencyTotals> rows = repository.summarizeByCurrency(USER, FROM, TO, null, null);

        assertThat(rows).extracting(ExpenseCurrencyTotals::getCurrency).containsExactly("USD", "VND");
        ExpenseCurrencyTotals usd = rows.get(0);
        assertThat(usd.getTotal()).isEqualByComparingTo("40.50");
        assertThat(usd.getFixed()).isEqualByComparingTo("0");
        assertThat(usd.getVariable()).isEqualByComparingTo("40.50");
        ExpenseCurrencyTotals vnd = rows.get(1);
        assertThat(vnd.getExpenseCount()).isEqualTo(3);
        assertThat(vnd.getTotal()).isEqualByComparingTo("5400000");
        assertThat(vnd.getFixed()).isEqualByComparingTo("5000000");
        assertThat(vnd.getVariable()).isEqualByComparingTo("400000");
    }

    @Test
    void coversAllRows_notJustOnePage() {
        for (int i = 0; i < 45; i++) {
            save(USER, "VND", "1000", i % 3 == 0 ? ExpenseType.FIXED : ExpenseType.VARIABLE, FROM.plusDays(i % 30), "n" + i);
        }

        ExpenseCurrencyTotals vnd = repository.summarizeByCurrency(USER, FROM, TO, null, null).get(0);

        assertThat(vnd.getExpenseCount()).isEqualTo(45);
        assertThat(vnd.getTotal()).isEqualByComparingTo("45000");
        assertThat(vnd.getFixed()).isEqualByComparingTo("15000");
        assertThat(vnd.getVariable()).isEqualByComparingTo("30000");
    }

    @Test
    void windowIsInclusive_andExcludesOutsideDates() {
        save(USER, "VND", "100", ExpenseType.VARIABLE, FROM, "first day");
        save(USER, "VND", "100", ExpenseType.VARIABLE, TO, "last day");
        save(USER, "VND", "999", ExpenseType.VARIABLE, FROM.minusDays(1), "before");
        save(USER, "VND", "999", ExpenseType.VARIABLE, TO.plusDays(1), "after");

        assertThat(repository.summarizeByCurrency(USER, FROM, TO, null, null).get(0).getTotal())
                .isEqualByComparingTo("200");
    }

    @Test
    void nullBounds_meanAllTime_andListAgrees() {
        save(USER, "VND", "100", ExpenseType.VARIABLE, LocalDate.of(2020, 1, 15), "old");
        save(USER, "VND", "200", ExpenseType.VARIABLE, LocalDate.of(2026, 3, 15), "recent");
        save(USER, "USD", "5", ExpenseType.FIXED, LocalDate.of(2024, 6, 1), "mid");

        List<ExpenseCurrencyTotals> rows = repository.summarizeByCurrency(USER, null, null, null, null);

        assertThat(rows).extracting(ExpenseCurrencyTotals::getCurrency).containsExactly("USD", "VND");
        assertThat(rows.get(1).getTotal()).isEqualByComparingTo("300");
        assertThat(rows.stream().mapToLong(ExpenseCurrencyTotals::getExpenseCount).sum())
                .isEqualTo(repository.search(USER, null, null, null, null, PageRequest.of(0, 1)).getTotalElements());
    }

    @Test
    void multiYearRange_isNotCapped() {
        save(USER, "VND", "100", ExpenseType.VARIABLE, LocalDate.of(2023, 2, 1), "a");
        save(USER, "VND", "100", ExpenseType.VARIABLE, LocalDate.of(2026, 2, 1), "b");

        assertThat(repository.summarizeByCurrency(USER, LocalDate.of(2023, 1, 1), LocalDate.of(2026, 12, 31), null, null)
                .get(0).getTotal()).isEqualByComparingTo("200");
    }

    @Test
    void excludesSoftDeleted_andOtherUsers() {
        save(USER, "VND", "100", ExpenseType.FIXED, FROM, "mine");
        Expense deleted = save(USER, "VND", "5000", ExpenseType.FIXED, FROM, "deleted");
        deleted.setDeleted(true);
        repository.saveAndFlush(deleted);
        save(OTHER, "VND", "7777", ExpenseType.FIXED, FROM, "theirs");

        ExpenseCurrencyTotals vnd = repository.summarizeByCurrency(USER, FROM, TO, null, null).get(0);

        assertThat(vnd.getTotal()).isEqualByComparingTo("100");
        assertThat(repository.summarizeByCurrency("nobody", FROM, TO, null, null)).isEmpty();
    }

    @Test
    void typeAndCategoryFilters_matchTheListQuery() {
        Expense food = save(USER, "VND", "300", ExpenseType.VARIABLE, FROM, "food");
        save(USER, "VND", "700", ExpenseType.FIXED, FROM, "rent");
        save(USER, "VND", "50", ExpenseType.VARIABLE, FROM.plusDays(1), "other-cat");
        Expense other = repository.findAll().stream().filter(e -> "other-cat".equals(e.getNote())).findFirst().orElseThrow();
        other.setCategoryId("cat-2");
        repository.saveAndFlush(other);

        // type filter: only FIXED -> spent == fixed, variable == 0
        ExpenseCurrencyTotals fixedOnly = repository.summarizeByCurrency(USER, FROM, TO, ExpenseType.FIXED, null).get(0);
        assertThat(fixedOnly.getTotal()).isEqualByComparingTo("700");
        assertThat(fixedOnly.getVariable()).isEqualByComparingTo("0");

        // category filter
        ExpenseCurrencyTotals cat1 = repository.summarizeByCurrency(USER, FROM, TO, null, food.getCategoryId()).get(0);
        assertThat(cat1.getTotal()).isEqualByComparingTo("1000");

        // for every filter combination the summary count equals the list's totalElements
        for (ExpenseType type : new ExpenseType[] {null, ExpenseType.FIXED, ExpenseType.VARIABLE}) {
            for (String cat : new String[] {null, "cat-1", "cat-2"}) {
                long listed = repository.search(USER, FROM, TO, type, cat, PageRequest.of(0, 1)).getTotalElements();
                long summarized = repository.summarizeByCurrency(USER, FROM, TO, type, cat).stream()
                        .mapToLong(ExpenseCurrencyTotals::getExpenseCount).sum();
                assertThat(summarized).as("type=%s category=%s", type, cat).isEqualTo(listed);
            }
        }
    }

    private Expense save(String userId, String currency, String amount, ExpenseType type, LocalDate on, String note) {
        return repository.saveAndFlush(Expense.builder()
                .userId(userId)
                .categoryId("cat-1")
                .amount(new BigDecimal(amount))
                .currency(currency)
                .expenseType(type)
                .spentOn(on)
                .note(note)
                .build());
    }
}
