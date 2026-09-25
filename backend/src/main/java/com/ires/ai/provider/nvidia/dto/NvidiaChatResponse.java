package com.ires.ai.provider.nvidia.dto;

import java.util.List;

public record NvidiaChatResponse(
        List<Choice> choices
) {

    public record Choice(
            Message message
    ) {
    }

    public record Message(
            String role,
            String content
    ) {
    }
}
