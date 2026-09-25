package com.ires.ai.dto.analysis;

import java.util.UUID;

public record QualityAnalysisRequest(
        UUID requirementId,
        String text
) {
}

