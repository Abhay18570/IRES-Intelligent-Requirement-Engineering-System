package com.ires.ai.dto.analysis;

import java.util.UUID;

public record ClassificationRequest(
        UUID requirementId,
        String text
) {
}

