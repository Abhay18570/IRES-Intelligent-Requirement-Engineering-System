package com.ires.ai.dto.analysis;

import java.util.UUID;

public record AmbiguityRequest(
        UUID requirementId,
        String text
) {
}

