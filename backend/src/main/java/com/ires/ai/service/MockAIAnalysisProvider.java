package com.ires.ai.service;

import com.ires.ai.dto.analysis.AmbiguityFinding;
import com.ires.ai.dto.analysis.AmbiguityRequest;
import com.ires.ai.dto.analysis.AmbiguityResponse;
import com.ires.ai.dto.analysis.ClassificationRequest;
import com.ires.ai.dto.analysis.ClassificationResponse;
import com.ires.ai.dto.analysis.CompletenessRequest;
import com.ires.ai.dto.analysis.CompletenessResponse;
import com.ires.ai.dto.analysis.ConflictDetectionRequest;
import com.ires.ai.dto.analysis.ConflictDetectionResponse;
import com.ires.ai.dto.analysis.ConflictFinding;
import com.ires.ai.dto.analysis.DuplicateCandidate;
import com.ires.ai.dto.analysis.DuplicateDetectionRequest;
import com.ires.ai.dto.analysis.DuplicateDetectionResponse;
import com.ires.ai.dto.analysis.MissingInformation;
import com.ires.ai.dto.analysis.QualityAnalysisRequest;
import com.ires.ai.dto.analysis.QualityAnalysisResponse;
import com.ires.ai.dto.analysis.QualityDimension;
import com.ires.ai.dto.analysis.RequirementCandidate;
import com.ires.requirement.entity.Requirement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MockAIAnalysisProvider implements AIAnalysisProvider {

    private final boolean available;

    public MockAIAnalysisProvider(@Value("${app.ai.mock-enabled:true}") boolean available) {
        this.available = available;
    }

    @Override
    public ClassificationResponse classify(ClassificationRequest request) {
        if (!available) {
            throw new IllegalStateException("AI analysis provider is unavailable.");
        }
        return new ClassificationResponse(
                "FUNCTIONAL",
                new BigDecimal("0.94"),
                "The requirement specifies observable system behavior and user interactions."
        );
    }

    @Override
    public AmbiguityResponse detectAmbiguity(AmbiguityRequest request) {
        if (!available) {
            throw new IllegalStateException("AI analysis provider is unavailable.");
        }
        List<AmbiguityFinding> findings = List.of(
                new AmbiguityFinding(
                        "respond quickly",
                        "The term does not define a measurable response time.",
                        "Specify an explicit maximum response latency threshold (e.g., within 500ms)."
                )
        );
        return new AmbiguityResponse(
                true,
                findings,
                new BigDecimal("0.89")
        );
    }

    @Override
    public CompletenessResponse analyzeCompleteness(CompletenessRequest request) {
        if (!available) {
            throw new IllegalStateException("AI analysis provider is unavailable.");
        }
        List<MissingInformation> missingInfo = List.of(
                new MissingInformation(
                        "Error Handling",
                        "Expected failure behavior and recovery steps are not defined."
                )
        );
        List<String> questions = List.of(
                "What error message should be displayed when the operation fails?",
                "How should the system retry or log failed attempts?"
        );
        return new CompletenessResponse(
                false,
                missingInfo,
                questions,
                new BigDecimal("0.86")
        );
    }

    @Override
    public QualityAnalysisResponse analyzeQuality(QualityAnalysisRequest request) {
        if (!available) {
            throw new IllegalStateException("AI analysis provider is unavailable.");
        }
        List<QualityDimension> dimensions = List.of(
                new QualityDimension("CLARITY", 85, "Requirement intent is generally clear.", "Reduce imprecise language."),
                new QualityDimension("SPECIFICITY", 70, "Some constraints lack explicit criteria.", "Include exact thresholds and boundaries."),
                new QualityDimension("TESTABILITY", 80, "Acceptance criteria can be derived with minor clarification.", "Define deterministic test assertions."),
                new QualityDimension("CONSISTENCY", 90, "Terminology is consistent with standard domain models.", "Maintain standardized naming."),
                new QualityDimension("ATOMICITY", 88, "Describes a single cohesive capability.", "Keep distinct concerns separated.")
        );
        return new QualityAnalysisResponse(
                82,
                dimensions,
                new BigDecimal("0.91")
        );
    }

    @Override
    public DuplicateDetectionResponse detectDuplicates(DuplicateDetectionRequest request) {
        if (!available) {
            throw new IllegalStateException("AI analysis provider is unavailable.");
        }
        List<DuplicateCandidate> duplicates = new ArrayList<>();
        if (request != null && request.candidateRequirements() != null && !request.candidateRequirements().isEmpty()) {
            RequirementCandidate first = request.candidateRequirements().get(0);
            duplicates.add(new DuplicateCandidate(
                    first.requirementId(),
                    new BigDecimal("0.88"),
                    "SIMILAR_FUNCTIONALITY",
                    "Both requirements describe similar functional intent and user workflows."
            ));
        }
        return new DuplicateDetectionResponse(
                duplicates,
                new BigDecimal("0.90")
        );
    }

    @Override
    public ConflictDetectionResponse detectConflicts(ConflictDetectionRequest request) {
        if (!available) {
            throw new IllegalStateException("AI analysis provider is unavailable.");
        }
        List<ConflictFinding> conflicts = new ArrayList<>();
        if (request != null && request.candidateRequirements() != null && !request.candidateRequirements().isEmpty()) {
            RequirementCandidate first = request.candidateRequirements().get(0);
            conflicts.add(new ConflictFinding(
                    first.requirementId(),
                    "LOGICAL_CONTRADICTION",
                    "MEDIUM",
                    "Conflicting constraints regarding access permissions or state transitions.",
                    "Align permission rules between the two requirements."
            ));
        }
        return new ConflictDetectionResponse(
                conflicts,
                new BigDecimal("0.87")
        );
    }

    @Override
    public AIAnalysisResult analyze(Requirement requirement) {
        if (!available) {
            throw new IllegalStateException("AI analysis provider is unavailable.");
        }
        String title = requirement.getTitle();
        return new AIAnalysisResult(
                "Development analysis generated for: " + title,
                new BigDecimal("18.00"),
                new BigDecimal("82.00"),
                new BigDecimal("82.00"),
                "Clarify measurable outcomes and acceptance conditions."
        );
    }
}

