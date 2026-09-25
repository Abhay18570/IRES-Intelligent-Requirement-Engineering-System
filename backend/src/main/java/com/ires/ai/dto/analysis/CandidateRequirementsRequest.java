package com.ires.ai.dto.analysis;

import java.util.List;
import java.util.UUID;

public record CandidateRequirementsRequest(
        List<UUID> candidateRequirementIds
) {
}
