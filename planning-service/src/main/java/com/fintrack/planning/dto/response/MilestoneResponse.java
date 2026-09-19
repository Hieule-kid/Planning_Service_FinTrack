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

    private BigDecimal targetSavings;

    private BigDecimal actualSaved;

    private MilestoneStatus status;
}
