package com.ires.ai.dto.analysis;

import java.math.BigDecimal;

public record ClassificationResponse(
        String classification,
        BigDecimal confidence,
        String reason
) {
}

