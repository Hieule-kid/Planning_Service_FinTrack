package com.fintrack.planning.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiPromptRequest(
        @NotBlank(message = "Prompt must not be blank")
        @Size(max = 2000, message = "Prompt must not exceed 2000 characters")
        String prompt
) {}
