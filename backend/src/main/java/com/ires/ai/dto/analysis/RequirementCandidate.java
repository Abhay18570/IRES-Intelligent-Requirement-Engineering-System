package com.ires.ai.dto.analysis;

import java.util.UUID;

public record RequirementCandidate(
        UUID requirementId,
        String text
) {
}

