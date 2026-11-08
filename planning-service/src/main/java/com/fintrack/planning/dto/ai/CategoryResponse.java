package com.fintrack.planning.dto.ai;

import java.math.BigDecimal;

public record CategoryResponse(
        String name,
        BigDecimal percentage,
        BigDecimal amount,
        String note
) {}
