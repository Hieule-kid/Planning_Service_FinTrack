package com.fintrack.planning.dto.request;

import com.fintrack.planning.model.enums.ExpenseType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request payload for creating a user-defined expense category.
 *
 * @author FinTrack Team
 * @since 1.0.0
 */
@Getter
@Setter
public class CreateExpenseCategoryRequest {

    @Schema(description = "Display name of the category — unique per user, case-insensitive", example = "Pet care")
    @NotBlank(message = "Category name is required")
    @Size(max = 50, message = "Category name must not exceed 50 characters")
    private String name;

    @Schema(description = "Default classification for expenses filed under this category")
    @NotNull(message = "Default type is required")
    private ExpenseType defaultType;
}
