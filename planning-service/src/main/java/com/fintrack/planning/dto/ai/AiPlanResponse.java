package com.fintrack.planning.dto.ai;

import java.math.BigDecimal;

import java.util.List;

public record AiPlanResponse(
        BigDecimal totalBudget,
        String currency,
        String term,
        List<CategoryResponse> categories,
        String aiAdvice
) {}
