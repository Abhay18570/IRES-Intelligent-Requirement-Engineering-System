package com.ires.ai.provider.nvidia.dto;

import java.util.List;

public record NvidiaChatRequest(
        String model,
        List<NvidiaChatMessage> messages
) {
}
