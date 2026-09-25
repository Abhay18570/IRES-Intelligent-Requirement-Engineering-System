package com.ires.ai.provider.jev.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record JevDecisionResponse(
        Integer code,
        String message,
        JsonNode data
) {
}