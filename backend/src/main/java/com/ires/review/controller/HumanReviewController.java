package com.ires.review.controller;

import com.ires.common.response.ApiResponse;
import com.ires.review.dto.HumanReviewRequest;
import com.ires.review.dto.HumanReviewResponse;
import com.ires.review.entity.ReviewArtifactType;
import com.ires.review.service.HumanReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class HumanReviewController {

    private final HumanReviewService humanReviewService;

    @PostMapping("/api/v1/ai-artifacts/{artifactType}/{artifactId}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_ANALYST')")
    public ApiResponse<HumanReviewResponse> review(
            @PathVariable ReviewArtifactType artifactType,
            @PathVariable UUID artifactId,
            @Valid @RequestBody HumanReviewRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ApiResponse.success("Artifact review completed.",
                humanReviewService.review(artifactType, artifactId, request, principal));
    }

    @GetMapping("/api/v1/ai-artifacts/{artifactType}/{artifactId}/reviews")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_ANALYST')")
    public ApiResponse<List<HumanReviewResponse>> history(
            @PathVariable ReviewArtifactType artifactType,
            @PathVariable UUID artifactId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ApiResponse.success("Artifact review history loaded.",
                humanReviewService.history(artifactType, artifactId, principal));
    }
}