package com.ires.ai.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.ires.requirement.entity.Requirement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requirement_ai_analysis")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequirementAIAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_id", nullable = false, unique = true)
    private Requirement requirement;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 20)
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(precision = 5, scale = 2)
    private BigDecimal ambiguityScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal completenessScore;

    @Column(precision = 5, scale = 2)
    private BigDecimal qualityScore;

    @Column(columnDefinition = "TEXT")
    private String suggestions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "classification_result", columnDefinition = "jsonb")
    private JsonNode classificationResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ambiguity_result", columnDefinition = "jsonb")
    private JsonNode ambiguityResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completeness_result", columnDefinition = "jsonb")
    private JsonNode completenessResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quality_result", columnDefinition = "jsonb")
    private JsonNode qualityResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "duplicate_result", columnDefinition = "jsonb")
    private JsonNode duplicateResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conflict_result", columnDefinition = "jsonb")
    private JsonNode conflictResult;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RequirementAIAnalysis(Requirement requirement) {
        this.requirement = requirement;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
