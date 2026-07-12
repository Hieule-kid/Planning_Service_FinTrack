package com.fintrack.planning.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fintrack.planning.model.enums.Currency;
import com.fintrack.planning.model.enums.Frequency;
import com.fintrack.planning.model.enums.PlanCategory;
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
 * <p>Accepts both the direct internal field names and the FE's field aliases:
 * <ul>
 *   <li>{@code title} → {@code goalTitle}</li>
 *   <li>{@code savingsPerPeriod} → {@code requiredPerPeriod}</li>
 *   <li>{@code duration} (flat months) → internally mapped to {@code durationInMonths}
 *       with {@code timeframeCategory} derived in the service layer</li>
 * </ul>
 *
 * <p>Frequency is accepted case-insensitively ({@code "monthly"} or {@code "MONTHLY"})
 * via {@code spring.jackson.mapper.accept-case-insensitive-enums: true}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Setter
public class CreatePlanRequest {

    @Schema(description = "Name of the savings goal", example = "Vacation Fund")
    @JsonAlias("title")
    @NotBlank(message = "Goal title is required")
    @Size(max = 50, message = "Goal title must not exceed 50 characters")
    private String goalTitle;

    @Schema(description = "Total amount to save", example = "10000000")
    @NotNull(message = "Target amount is required")
    @DecimalMin(value = "0.01", message = "Target amount must be positive")
    private BigDecimal targetAmount;

    @Schema(description = "Currency of the plan amounts", example = "VND")
    @NotNull(message = "Currency is required")
    private Currency currency;

    @Schema(description = "Semantic category of the goal (emergency, travel, house, etc.)")
    private PlanCategory planCategory;

    @Schema(description = "Flat duration in months — the FE sends this field. The service derives " +
            "timeframeCategory and durationInMonths from it if timeframeCategory is not supplied.")
    @Min(value = 3, message = "Duration must be at least 3 months")
    private Integer duration;

    @Schema(description = "Overall time horizon — derived from duration when omitted")
    private TimeframeCategory timeframeCategory;

    @Schema(description = "Duration in months — set by the service from the flat duration field; " +
            "can also be supplied directly for short/mid-term plans.", example = "6")
    @Min(value = 3, message = "Duration in months must be at least 3")
    private Integer durationInMonths;

    @Schema(description = "Duration in years — legacy field for long-term plans (1–20 years).", example = "5")
    @Min(value = 1, message = "Duration in years must be at least 1")
    @Max(value = 20, message = "Duration in years must not exceed 20")
    private Integer durationInYears;

    @Schema(description = "How often milestones recur — accepted in any case (monthly, MONTHLY)")
    @NotNull(message = "Frequency is required")
    private Frequency frequency;

    @Schema(description = "Amount the user commits to saving per interval", example = "1666666")
    @JsonAlias("savingsPerPeriod")
    @NotNull(message = "Required per period is required")
    @DecimalMin(value = "0.01", message = "Required per period must be positive")
    private BigDecimal requiredPerPeriod;

    @Schema(description = "The date the plan and its first milestone start — defaults to today when omitted",
            example = "2026-07-11")
    private LocalDate startDate;
}
