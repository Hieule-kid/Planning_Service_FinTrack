package com.fintrack.planning.dto.request;

import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.TimeframeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request payload for creating a new savings plan.
 *
 * <p>On submission, the service validates the {@code timeframeCategory}/{@code frequency}
 * combination and the matching duration field, then generates the full milestone
 * schedule for the plan.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Setter
public class CreatePlanRequest {

    @Schema(description = "Name of the savings goal", example = "Emergency Fund")
    @NotBlank(message = "Goal title is required")
    @Size(max = 50, message = "Goal title must not exceed 50 characters")
    private String goalTitle;

    @Schema(description = "Total amount to save", example = "12000.00")
    @NotNull(message = "Target amount is required")
    @DecimalMin(value = "0.01", message = "Target amount must be positive")
    private BigDecimal targetAmount;

    @Schema(description = "Overall time horizon of the goal")
    @NotNull(message = "Timeframe category is required")
    private TimeframeCategory timeframeCategory;

    @Schema(description = "Duration in months — required when timeframeCategory is SHORT_TERM (3-12)", example = "6")
    @Min(value = 3, message = "Short-term duration must be between 3 and 12 months")
    @Max(value = 12, message = "Short-term duration must be between 3 and 12 months")
    private Integer durationInMonths;

    @Schema(description = "Duration in years — required when timeframeCategory is LONG_TERM (1-30)", example = "5")
    @Min(value = 1, message = "Long-term duration must be between 1 and 30 years")
    @Max(value = 30, message = "Long-term duration must be between 1 and 30 years")
    private Integer durationInYears;

    @Schema(description = "How often milestones recur")
    @NotNull(message = "Frequency is required")
    private Frequency frequency;

    @Schema(description = "Amount the user commits to saving per interval", example = "500.00")
    @NotNull(message = "Required per period is required")
    @DecimalMin(value = "0.01", message = "Required per period must be positive")
    private BigDecimal requiredPerPeriod;

    @Schema(description = "The date the plan and its first milestone start", example = "2026-01-01")
    @NotNull(message = "Start date is required")
    private LocalDate startDate;
}
