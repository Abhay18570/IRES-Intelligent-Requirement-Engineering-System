package com.ires.ai.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

public record DuplicateDetectionResponse(
        List<DuplicateCandidate> duplicates,
        BigDecimal confidence
) {
}

