package com.ires.ai.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

public record AmbiguityResponse(
        boolean hasAmbiguity,
        List<AmbiguityFinding> findings,
        BigDecimal confidence
) {
}

