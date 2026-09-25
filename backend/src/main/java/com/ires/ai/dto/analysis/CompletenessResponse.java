package com.ires.ai.dto.analysis;

import java.math.BigDecimal;
import java.util.List;

public record CompletenessResponse(
        boolean isComplete,
        List<MissingInformation> missingInformation,
        List<String> clarificationQuestions,
        BigDecimal confidence
) {
}

