package com.ires.ai.dto.analysis;

import java.math.BigDecimal;
import java.util.UUID;

public record RequirementImprovementResponse(
        UUID requirementId,
        String originalTitle,
        String proposedTitle,
        String originalDescription,
        String proposedDescription,
        String rationale,
        BigDecimal confidence
) {
}