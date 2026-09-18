package com.fintrack.planning.dto.ai;

public record CategoryResponse(
        String name,
        double percentage,
        double amount,
        String note
) {}
