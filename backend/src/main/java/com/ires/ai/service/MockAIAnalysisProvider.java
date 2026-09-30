package com.ires.ai.service;

import com.ires.ai.dto.analysis.AmbiguityFinding;
import com.ires.ai.dto.analysis.AmbiguityRequest;
import com.ires.ai.dto.analysis.AmbiguityResponse;
import com.ires.ai.dto.analysis.AcceptanceCriteriaGenerationResponse;
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
import com.ires.ai.dto.analysis.RequirementImprovementResponse;
import com.ires.ai.dto.analysis.RequirementCandidate;
import com.ires.ai.dto.srs.SrsGenerationRequest;
import com.ires.ai.dto.srs.SrsGenerationResponse;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementType;
import com.ires.requirement.criteria.entity.CriteriaType;
import com.ires.story.dto.GeneratedUserStory;
import com.ires.story.entity.UserStory;
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
    public RequirementImprovementResponse improveRequirement(Requirement requirement) {
        if (!available) {
            throw new IllegalStateException("AI analysis provider is unavailable.");
        }
        String title = requirement.getTitle() == null ? "" : requirement.getTitle();
        String description = requirement.getDescription();
        return new RequirementImprovementResponse(
                requirement.getId(),
                title,
                title,
                description,
                description,
                "The mock provider returned a deterministic requirement improvement proposal.",
                new BigDecimal("0.90")
        );
    }

    @Override
    public GeneratedUserStory generateUserStory(Requirement requirement) {
        if (!available) {
            throw new IllegalStateException("AI analysis provider is unavailable.");
        }
        String title = requirement.getTitle() == null ? "User story" : requirement.getTitle();
        String description = requirement.getDescription();
        if (description == null || description.isBlank()) {
            description = "User story generated from requirement: " + title;
        }
        return new GeneratedUserStory(
                title,
                description,
                "As a user, I want " + title + " so that the requirement delivers its intended value.",
                requirement.getPriority() == null ? RequirementPriority.MEDIUM : requirement.getPriority()
        );
    }

            @Override
            public AcceptanceCriteriaGenerationResponse generateAcceptanceCriteria(
                Requirement requirement,
                UserStory userStory
            ) {
            if (!available) {
                throw new IllegalStateException("AI analysis provider is unavailable.");
            }
            String subject = userStory == null ? requirement.getTitle() : userStory.getTitle();
            if (subject == null || subject.isBlank()) {
                subject = "the requirement";
            }
            return new AcceptanceCriteriaGenerationResponse(List.of(
                new AcceptanceCriteriaGenerationResponse.GeneratedCriterion(
                    "Expected behavior is completed",
                    "Given " + subject + " is available, when the expected action is performed, then the system completes the behavior described by the requirement.",
                    CriteriaType.BEHAVIORAL
                ),
                new AcceptanceCriteriaGenerationResponse.GeneratedCriterion(
                    "Invalid input is handled",
                    "Given input does not satisfy the requirement, when the action is attempted, then the system rejects the input without completing the action.",
                    CriteriaType.VALIDATION
                )
            ));
            }

        @Override
        public SrsGenerationResponse generateSrs(SrsGenerationRequest request) {
            if (!available) {
                throw new IllegalStateException("AI analysis provider is unavailable.");
            }
            if (request == null || request.requirements() == null || request.requirements().isEmpty()) {
                throw new IllegalArgumentException("At least one requirement is required to generate an SRS.");
            }

            List<String> functional = new ArrayList<>();
            List<String> nonFunctional = new ArrayList<>();
            List<String> business = new ArrayList<>();
            List<String> technical = new ArrayList<>();
            request.requirements().forEach(requirement -> {
                String text = requirement.description() == null || requirement.description().isBlank()
                        ? requirement.title()
                        : requirement.description();
                switch (requirement.requirementType()) {
                    case FUNCTIONAL -> functional.add(text);
                    case NON_FUNCTIONAL -> nonFunctional.add(text);
                    case BUSINESS -> business.add(text);
                    case TECHNICAL -> technical.add(text);
                }
            });

            String projectName = request.projectName() == null || request.projectName().isBlank()
                    ? "Project"
                    : request.projectName();
            return new SrsGenerationResponse(
                    projectName + " Software Requirements Specification",
                    "Draft specification for " + projectName + " based on " + request.requirements().size() + " requirements.",
                    List.copyOf(functional),
                    List.copyOf(nonFunctional),
                    List.copyOf(business),
                    List.copyOf(technical),
                    List.of("The supplied project requirements represent the current project scope."),
                    List.of()
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

