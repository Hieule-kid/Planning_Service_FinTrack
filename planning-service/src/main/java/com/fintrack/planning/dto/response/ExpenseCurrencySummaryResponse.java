package com.fintrack.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Spent / fixed / variable totals for one currency. Money is serialized as strings (lossless for JS).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseCurrencySummaryResponse {

    private String currency;
    private long count;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal total;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal fixed;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal variable;
}
