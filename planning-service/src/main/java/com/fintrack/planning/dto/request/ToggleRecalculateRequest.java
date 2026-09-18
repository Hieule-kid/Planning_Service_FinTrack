package com.fintrack.planning.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Request payload for toggling a plan's deficit-redistribution setting.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Setter
public class ToggleRecalculateRequest {

    @Schema(description = "Whether missed-deadline deficits should be redistributed across future milestones", example = "true")
    @NotNull(message = "recalculateOnMissedDeadline is required")
    private Boolean recalculateOnMissedDeadline;
}
