package com.fintrack.planning.dto.ai;

import jakarta.validation.constraints.NotBlank;

public record AiPromptRequest(
        @NotBlank(message = "Prompt must not be blank")
        String prompt
) {}
