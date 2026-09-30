package com.ires.srs.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ires.srs.entity.SrsDocument;
import com.ires.srs.entity.SrsDocumentStatus;

import java.time.Instant;
import java.util.UUID;

public record SrsDocumentResponse(
        UUID id,
        UUID projectId,
        String title,
        JsonNode content,
        SrsDocumentStatus status,
        Instant generatedAt,
        Instant createdAt
) {
    public static SrsDocumentResponse from(SrsDocument document) {
        return new SrsDocumentResponse(
                document.getId(),
                document.getProject().getId(),
                document.getTitle(),
                document.getContent(),
                document.getStatus(),
                document.getGeneratedAt(),
                document.getCreatedAt()
        );
    }
}