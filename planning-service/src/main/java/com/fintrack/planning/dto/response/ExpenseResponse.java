package com.fintrack.planning.dto.response;

import com.fintrack.planning.model.enums.ExpenseSource;
import com.fintrack.planning.model.enums.ExpenseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Outbound view of an {@link com.fintrack.planning.model.Expense}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseResponse {

    private String id;
    private String planId;
    private String categoryId;
    private BigDecimal amount;
    private String currency;
    private ExpenseType expenseType;
    private LocalDate spentOn;
    private String note;
    private ExpenseSource source;
}
