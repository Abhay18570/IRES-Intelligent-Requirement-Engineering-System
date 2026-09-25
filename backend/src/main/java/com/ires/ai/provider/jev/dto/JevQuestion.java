package com.ires.ai.provider.jev.dto;

public record JevQuestion(
        String type,
        String instructions,
        Object criteria
) {
}