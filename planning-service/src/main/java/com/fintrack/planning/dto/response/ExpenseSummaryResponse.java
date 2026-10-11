package com.fintrack.planning.dto.response;

import com.fintrack.planning.model.enums.ExpenseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Totals over all expenses matching a filter, independent of list paging, together with the filter
 * that was actually applied. {@code from}/{@code to} are always the resolved window (the default
 * window when the caller sent no dates); {@code type} and {@code categoryId} are null when unfiltered.
 * Currencies are never combined: {@code totals} has one entry per currency.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSummaryResponse {

    private LocalDate from;
    private LocalDate to;
    private ExpenseType type;
    private String categoryId;
    private List<ExpenseCurrencySummaryResponse> totals;
}
