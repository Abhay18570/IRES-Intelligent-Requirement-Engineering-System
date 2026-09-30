package com.ires.srs.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.ires.project.entity.Project;
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
@Table(name = "project_srs_documents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SrsDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 300)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SrsDocumentStatus status = SrsDocumentStatus.DRAFT;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SrsDocument(Project project, String title, JsonNode content) {
        this.project = project;
        this.title = title;
        this.content = content;
        this.status = SrsDocumentStatus.DRAFT;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        generatedAt = now;
        createdAt = now;
    }
}