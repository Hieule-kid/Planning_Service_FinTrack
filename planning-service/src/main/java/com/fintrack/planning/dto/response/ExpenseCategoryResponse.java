package com.fintrack.planning.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fintrack.planning.model.enums.ExpenseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Outbound view of an {@link com.fintrack.planning.model.ExpenseCategory}.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseCategoryResponse {

    private String id;

    private String name;

    private ExpenseType defaultType;

    /** {@code true} for auto-seeded defaults, {@code false} for user-created categories. */
    @JsonProperty("isSystem")
    private boolean system;
}
