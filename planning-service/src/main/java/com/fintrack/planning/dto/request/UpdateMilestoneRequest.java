package com.fintrack.planning.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 */
@Getter
@Setter
public class UpdateMilestoneRequest {

    @Schema(description = "New actual-saved amount for this milestone", example = "250.00")
    @NotNull(message = "Actual saved amount is required")
    @DecimalMin(value = "0.00", message = "Actual saved amount cannot be negative")
    private BigDecimal actualSaved;
}
