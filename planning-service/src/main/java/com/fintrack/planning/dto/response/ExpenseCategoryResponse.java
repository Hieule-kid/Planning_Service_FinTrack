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
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseCategoryResponse {

    private String id;

    private String name;

    private ExpenseType defaultType;

    @JsonProperty("isSystem")
    private boolean system;
}
