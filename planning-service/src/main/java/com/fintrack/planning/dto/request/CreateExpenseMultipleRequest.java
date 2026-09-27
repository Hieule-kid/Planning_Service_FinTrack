package com.fintrack.planning.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpenseMultipleRequest {
    @NotEmpty
    @Size(max=50)
    @Valid
    List<CreateExpenseRequest> expenses;
}
