package com.ires.ai.dto.analysis;

import java.util.List;
import java.util.UUID;

public record ConflictDetectionRequest(
        UUID requirementId,
        String targetRequirement,
        List<RequirementCandidate> candidateRequirements
) {
}

