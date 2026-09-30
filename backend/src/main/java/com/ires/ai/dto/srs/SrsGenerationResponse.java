package com.ires.ai.dto.srs;

import java.util.List;

public record SrsGenerationResponse(
        String title,
        String overview,
        List<String> functionalRequirements,
        List<String> nonFunctionalRequirements,
        List<String> businessRequirements,
        List<String> technicalRequirements,
        List<String> assumptions,
        List<String> constraints
) {
}