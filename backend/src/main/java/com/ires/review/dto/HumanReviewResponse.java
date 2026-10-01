package com.ires.review.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ires.project.dto.UserSummary;
import com.ires.review.entity.HumanReview;
import com.ires.review.entity.ReviewAction;
import com.ires.review.entity.ReviewArtifactType;

import java.time.Instant;
import java.util.UUID;

public record HumanReviewResponse(
        UUID id,
        ReviewArtifactType artifactType,
        UUID artifactId,
        ReviewAction action,
        String artifactStatus,
        UserSummary reviewer,
        String reason,
        JsonNode previousContent,
        JsonNode reviewedContent,
        Instant reviewedAt
) {
    public static HumanReviewResponse from(HumanReview review) {
        return new HumanReviewResponse(
                review.getId(),
                review.getArtifactType(),
                review.getArtifactId(),
                review.getAction(),
                review.getArtifactStatus(),
                UserSummary.from(review.getReviewer()),
                review.getReason(),
                review.getPreviousContent(),
                review.getReviewedContent(),
                review.getReviewedAt()
        );
    }
}