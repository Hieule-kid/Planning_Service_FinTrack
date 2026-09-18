package com.fintrack.planning.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for recording a new expense.
 *
 * <p>The {@code expenseType} is not accepted here — it is derived from the
 * chosen category's {@code defaultType} at create time. Use
 * {@link UpdateExpenseRequest} to override it afterwards.
 *
 * <p>{@code spentOn} must not be in the future; that check lives in the service
 * layer (it needs "today" at request time, not a Bean Validation constant).
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Setter
public class CreateExpenseRequest {

    @Schema(description = "Amount spent — must be at least 0.01", example = "42.50")
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    @Schema(description = "ISO-4217 currency code", example = "USD")
    @NotNull(message = "Currency is required")
    private String currency;

    @Schema(description = "ID of the expense category to file this under")
    @NotBlank(message = "Category is required")
    private String categoryId;

    @Schema(description = "The date the money was spent — cannot be in the future", example = "2026-08-29")
    @NotNull(message = "Spent-on date is required")
    private LocalDate spentOn;

    @Schema(description = "Optional free-text note", example = "Team lunch")
    @Size(max = 255, message = "Note must not exceed 255 characters")
    private String note;

    @Schema(description = "Optional ID of a savings plan to associate this expense with")
    private String planId;
}
