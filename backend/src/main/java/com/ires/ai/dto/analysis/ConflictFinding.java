package com.ires.ai.dto.analysis;

import java.util.UUID;

public record ConflictFinding(
        UUID requirementId,
        String conflictType,
        String severity,
        String reason,
        String suggestion
) {
}

