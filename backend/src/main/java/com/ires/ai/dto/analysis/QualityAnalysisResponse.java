package com.ires.ai.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

public record QualityAnalysisResponse(
        Integer overallScore,
        List<QualityDimension> dimensions,
        BigDecimal confidence
) {
}

