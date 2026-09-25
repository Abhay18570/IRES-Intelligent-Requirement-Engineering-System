package com.ires.ai.dto.analysis;

import java.util.UUID;

public record CompletenessRequest(
        UUID requirementId,
        String text
) {
}

