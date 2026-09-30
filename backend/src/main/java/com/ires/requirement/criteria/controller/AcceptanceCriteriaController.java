package com.ires.requirement.criteria.controller;

import com.ires.common.response.ApiResponse;
import com.ires.requirement.criteria.dto.AcceptanceCriteriaCreateRequest;
import com.ires.requirement.criteria.dto.AcceptanceCriteriaGenerationRequest;
import com.ires.requirement.criteria.dto.AcceptanceCriteriaResponse;
import com.ires.requirement.criteria.dto.AcceptanceCriteriaUpdateRequest;
import com.ires.requirement.criteria.service.AcceptanceCriteriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AcceptanceCriteriaController {

    private final AcceptanceCriteriaService criteriaService;

    @PostMapping("/api/v1/requirements/{requirementId}/acceptance-criteria")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_ANALYST')")
    public ResponseEntity<ApiResponse<AcceptanceCriteriaResponse>> create(
            @PathVariable UUID requirementId,
            @Valid @RequestBody AcceptanceCriteriaCreateRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Acceptance criteria created.", criteriaService.create(requirementId, request, principal)));
    }

            @PostMapping("/api/v1/requirements/{requirementId}/acceptance-criteria/generate")
            @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_ANALYST')")
            public ResponseEntity<ApiResponse<List<AcceptanceCriteriaResponse>>> generate(
                @PathVariable UUID requirementId,
                @Valid @RequestBody(required = false) AcceptanceCriteriaGenerationRequest request,
                @AuthenticationPrincipal UserDetails principal
            ) {
            UUID userStoryId = request == null ? null : request.userStoryId();
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Acceptance criteria generated.", criteriaService.generate(requirementId, userStoryId, principal)));
            }

    @GetMapping("/api/v1/requirements/{requirementId}/acceptance-criteria")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<AcceptanceCriteriaResponse>> list(
            @PathVariable UUID requirementId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ApiResponse.success("Acceptance criteria loaded.", criteriaService.list(requirementId, pageable, principal));
    }

    @PutMapping("/api/v1/acceptance-criteria/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_ANALYST')")
    public ApiResponse<AcceptanceCriteriaResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AcceptanceCriteriaUpdateRequest request,
            @AuthenticationPrincipal UserDetails principal
    ) {
        return ApiResponse.success("Acceptance criteria updated.", criteriaService.update(id, request, principal));
    }

    @DeleteMapping("/api/v1/acceptance-criteria/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'BUSINESS_ANALYST')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        criteriaService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
