package com.ires.review.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.ires.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "human_reviews")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HumanReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 30)
    private ReviewArtifactType artifactType;

    @Column(name = "artifact_id", nullable = false)
    private UUID artifactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewAction action;

    @Column(name = "artifact_status", nullable = false, length = 20)
    private String artifactStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_content", nullable = false, columnDefinition = "jsonb")
    private JsonNode previousContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reviewed_content", nullable = false, columnDefinition = "jsonb")
    private JsonNode reviewedContent;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    public HumanReview(ReviewArtifactType artifactType, UUID artifactId, ReviewAction action,
                       String artifactStatus, User reviewer, String reason,
                       JsonNode previousContent, JsonNode reviewedContent) {
        this.artifactType = artifactType;
        this.artifactId = artifactId;
        this.action = action;
        this.artifactStatus = artifactStatus;
        this.reviewer = reviewer;
        this.reason = reason;
        this.previousContent = previousContent;
        this.reviewedContent = reviewedContent;
    }

    @PrePersist
    void onCreate() {
        reviewedAt = Instant.now();
    }
}