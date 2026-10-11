package com.fintrack.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fintrack.planning.model.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Aggregate totals of all of a user's plans in a single currency, used by the dashboard summary.
 *
 * <p>Currencies are never combined: a user with VND and USD plans gets one entry per currency.
 * Money fields are serialized as strings so large VND amounts survive a JavaScript client
 * without floating-point loss.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrencySummaryResponse {

    private Currency currency;
    private long planCount;
    private long completedPlanCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalTarget;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalSaved;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal remaining;
}
