package com.fintrack.planning.dto.ai;

import java.util.List;

public record AiPlanResponse(
        double totalBudget,
        String currency,
        String term,
        List<CategoryResponse> categories,
        String aiAdvice
) {}
