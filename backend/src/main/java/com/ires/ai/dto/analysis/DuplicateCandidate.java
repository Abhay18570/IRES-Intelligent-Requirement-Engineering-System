package com.ires.ai.dto.analysis;

import java.math.BigDecimal;
import java.util.UUID;

public record DuplicateCandidate(
        UUID requirementId,
        BigDecimal similarity,
        String relationship,
        String reason
) {
}

