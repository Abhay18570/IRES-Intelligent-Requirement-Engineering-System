package com.ires.ai.provider.jev.dto;

import java.util.Map;

public record JevDecisionRequest(
        String model,
        Object state,
        Map<String, JevQuestion> questions
) {
}