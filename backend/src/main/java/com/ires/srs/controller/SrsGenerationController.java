package com.ires.srs.controller;

import com.ires.common.response.ApiResponse;
import com.ires.srs.dto.SrsDocumentResponse;
import com.ires.srs.service.SrsGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class SrsGenerationController {

    private final SrsGenerationService srsGenerationService;

    @PostMapping("/api/v1/projects/{projectId}/srs/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_ANALYST')")
    public ResponseEntity<ApiResponse<SrsDocumentResponse>> generate(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "SRS generated as a draft.", srsGenerationService.generate(projectId, principal)));
    }
}