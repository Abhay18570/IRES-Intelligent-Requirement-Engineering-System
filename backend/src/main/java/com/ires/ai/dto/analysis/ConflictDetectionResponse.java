package com.ires.ai.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

public record ConflictDetectionResponse(
        List<ConflictFinding> conflicts,
        BigDecimal confidence
) {
}

