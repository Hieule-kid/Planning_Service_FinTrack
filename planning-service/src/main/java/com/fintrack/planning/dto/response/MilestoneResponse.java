package com.fintrack.planning.dto.response;

import com.fintrack.planning.model.enums.MilestoneStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single milestone within a plan's schedule.
 *
 * <p>{@link #targetSavings} and {@link #status} are always the live, recomputed
 * values — reflecting any deficit redistribution and the current date — never the
 * raw persisted snapshot.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneResponse {

    private String id;
    private String planId;
    private int sequenceIndex;
    private String timeline;
    private LocalDate periodDate;
    private LocalDate deadline;
    private BigDecimal baseTargetSavings;

    /** Live, effective target — {@link #baseTargetSavings} plus any redistributed deficit. */
    private BigDecimal targetSavings;

    private BigDecimal actualSaved;

    /** Live status, recomputed against the current date — never a stale cached value. */
    private MilestoneStatus status;
}
