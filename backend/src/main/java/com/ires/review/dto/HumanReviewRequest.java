package com.ires.review.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ires.review.entity.ReviewAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HumanReviewRequest(
        @NotNull ReviewAction action,
        @Size(max = 1000) String reason,
        JsonNode editedContent
) {
}