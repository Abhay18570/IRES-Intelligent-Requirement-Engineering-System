package com.ires.ai.provider.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.ires.ai.dto.analysis.AmbiguityRequest;
import com.ires.ai.dto.analysis.AmbiguityResponse;
import com.ires.ai.dto.analysis.ClassificationRequest;
import com.ires.ai.dto.analysis.ClassificationResponse;
import com.ires.ai.dto.analysis.CompletenessRequest;
import com.ires.ai.dto.analysis.CompletenessResponse;
import com.ires.ai.dto.analysis.ConflictDetectionRequest;
import com.ires.ai.dto.analysis.ConflictDetectionResponse;
import com.ires.ai.dto.analysis.DuplicateDetectionRequest;
import com.ires.ai.dto.analysis.DuplicateDetectionResponse;
import com.ires.ai.dto.analysis.QualityAnalysisRequest;
import com.ires.ai.dto.analysis.QualityAnalysisResponse;
import com.ires.ai.provider.jev.dto.JevDecisionRequest;
import com.ires.ai.provider.jev.dto.JevDecisionResponse;
import com.ires.ai.provider.jev.dto.JevQuestion;
import com.ires.ai.service.AIAnalysisProvider;
import com.ires.requirement.entity.Requirement;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class JevAIAnalysisProvider implements AIAnalysisProvider {

    private final JevApiClient client;
    private final String model;

    public JevAIAnalysisProvider(
            JevApiClient client,
            String model
    ) {
        this.client = client;
        this.model = model;
    }

    @Override
    public ClassificationResponse classify(ClassificationRequest request) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("requirement_id", request.requirementId());
        state.put("requirement_text", request.text());

        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put(
                "FUNCTIONAL",
                "Describes behavior or functionality that the system must provide."
        );
        criteria.put(
                "NON_FUNCTIONAL",
                "Describes a quality attribute, performance characteristic, constraint, or other non-functional property."
        );
        criteria.put(
                "BUSINESS",
                "Describes a business goal, business rule, or organizational need."
        );
        criteria.put(
                "TECHNICAL",
                "Describes a technical implementation, architecture, infrastructure, or technology constraint."
        );
        criteria.put(
                "UNCLEAR",
                "The requirement cannot be reliably classified from the supplied text."
        );

        Map<String, JevQuestion> questions = Map.of(
                "classification",
                new JevQuestion(
                        "choice",
                        "Classify the supplied software requirement into exactly one requirement type.",
                        criteria
                )
        );

        JevDecisionRequest jevRequest = new JevDecisionRequest(
                resolveModel(),
                state,
                questions
        );

        JevDecisionResponse response = client.decide(jevRequest);

        validateResponse(response);

        JsonNode answer = response.data()
                .path("answers")
                .path("classification");

        String classification = answer.path("choice").asText(null);

        if (classification == null || classification.isBlank()) {
            throw new IllegalStateException(
                    "Jev response did not contain a classification choice."
            );
        }

        BigDecimal confidence = answer.has("confidence")
                ? answer.get("confidence").decimalValue()
                : BigDecimal.ZERO;
        if (!Set.of(
                "FUNCTIONAL",
                "NON_FUNCTIONAL",
                "BUSINESS",
                "TECHNICAL",
                "UNCLEAR"
        ).contains(classification)) {
        throw new IllegalStateException(
                "Jev response contained an unsupported classification: " + classification
        );
        }

        if (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
        throw new IllegalStateException(
                "Jev response contained an invalid classification confidence: " + confidence
        );
        }

        String reason = switch (classification) {
            case "FUNCTIONAL" ->
                    "The requirement was classified as FUNCTIONAL because it describes system behavior or functionality.";
            case "NON_FUNCTIONAL" ->
                    "The requirement was classified as NON_FUNCTIONAL because it describes a quality attribute or constraint.";
            case "BUSINESS" ->
                    "The requirement was classified as BUSINESS because it describes a business goal, rule, or organizational need.";
            case "TECHNICAL" ->
                    "The requirement was classified as TECHNICAL because it describes a technical implementation or infrastructure constraint.";
            default ->
                    "The requirement could not be reliably classified into a specific requirement type.";
        };

        return new ClassificationResponse(
                classification,
                confidence,
                reason
        );
    }

    private String resolveModel() {
        if (model == null || model.isBlank()) {
            return "typesafe-ai/jev";
        }

        return model;
    }

    private void validateResponse(JevDecisionResponse response) {
        if (response == null) {
            throw new IllegalStateException(
                    "Jev returned an empty response."
            );
        }

        if (response.code() == null || response.code() != 0) {
            throw new IllegalStateException(
                    "Jev decision failed: " + response.message()
            );
        }

        if (response.data() == null) {
            throw new IllegalStateException(
                    "Jev response did not contain decision data."
            );
        }
    }

    /*
     * These capabilities will be implemented in subsequent vertical slices.
     */
    @Override
    public AmbiguityResponse detectAmbiguity(AmbiguityRequest request) {
        throw new UnsupportedOperationException(
                "Jev ambiguity detection is not implemented yet."
        );
    }

    @Override
    public CompletenessResponse analyzeCompleteness(
            CompletenessRequest request
    ) {
        throw new UnsupportedOperationException(
                "Jev completeness analysis is not implemented yet."
        );
    }

    @Override
    public QualityAnalysisResponse analyzeQuality(
            QualityAnalysisRequest request
    ) {
        throw new UnsupportedOperationException(
                "Jev quality analysis is not implemented yet."
        );
    }

    @Override
    public DuplicateDetectionResponse detectDuplicates(
            DuplicateDetectionRequest request
    ) {
        throw new UnsupportedOperationException(
                "Jev duplicate detection is not implemented yet."
        );
    }

    @Override
    public ConflictDetectionResponse detectConflicts(
            ConflictDetectionRequest request
    ) {
        throw new UnsupportedOperationException(
                "Jev conflict detection is not implemented yet."
        );
    }

    @Override
    public AIAnalysisResult analyze(Requirement requirement) {
        throw new UnsupportedOperationException(
                "Legacy AI analysis is not implemented by the Jev provider."
        );
    }
}