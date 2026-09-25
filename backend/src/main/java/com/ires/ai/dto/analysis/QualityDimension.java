package com.ires.ai.dto.analysis;

public record QualityDimension(
        String name,
        Integer score,
        String finding,
        String recommendation
) {
}

