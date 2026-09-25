package com.ires.ai.dto.analysis;

public record AmbiguityFinding(
        String text,
        String reason,
        String suggestion
) {
}

