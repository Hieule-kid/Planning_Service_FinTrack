package com.fintrack.planning.dto.request;

import com.fintrack.planning.model.enums.ExpenseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial-update payload for an existing expense (PATCH semantics — every field
 * is optional; {@code null} means "leave unchanged").
 *
 * <p>This is the only place a user can override {@code expenseType} after the
 * expense was created.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Setter
public class UpdateExpenseRequest {

    @Schema(description = "New amount — must be at least 0.01 when supplied")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    @Schema(description = "New ISO-4217 currency code")
    private String currency;

    @Schema(description = "New category ID")
    private String categoryId;

    @Schema(description = "Explicit fixed/variable override for this expense")
    private ExpenseType expenseType;

    @Schema(description = "New spent-on date — cannot be in the future")
    private LocalDate spentOn;

    @Schema(description = "New note — max 255 characters")
    @Size(max = 255, message = "Note must not exceed 255 characters")
    private String note;

    @Schema(description = "New plan association; not cleared when null (omitted)")
    private String planId;
}
