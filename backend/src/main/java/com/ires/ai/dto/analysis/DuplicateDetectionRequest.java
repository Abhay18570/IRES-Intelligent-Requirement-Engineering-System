package com.ires.ai.dto.analysis;

import java.util.List;
import java.util.UUID;

public record DuplicateDetectionRequest(
        UUID requirementId,
        String targetRequirement,
        List<RequirementCandidate> candidateRequirements
) {
}

