package com.ires.ai.service;

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
import com.ires.requirement.entity.Requirement;

import java.math.BigDecimal;

public interface AIAnalysisProvider {

    default ClassificationResponse classify(ClassificationRequest request) {
        throw new UnsupportedOperationException("Classification analysis is not implemented.");
    }

    default AmbiguityResponse detectAmbiguity(AmbiguityRequest request) {
        throw new UnsupportedOperationException("Ambiguity detection is not implemented.");
    }

    default CompletenessResponse analyzeCompleteness(CompletenessRequest request) {
        throw new UnsupportedOperationException("Completeness analysis is not implemented.");
    }

    default QualityAnalysisResponse analyzeQuality(QualityAnalysisRequest request) {
        throw new UnsupportedOperationException("Quality analysis is not implemented.");
    }

    default DuplicateDetectionResponse detectDuplicates(DuplicateDetectionRequest request) {
        throw new UnsupportedOperationException("Duplicate detection is not implemented.");
    }

    default ConflictDetectionResponse detectConflicts(ConflictDetectionRequest request) {
        throw new UnsupportedOperationException("Conflict detection is not implemented.");
    }

    AIAnalysisResult analyze(Requirement requirement);

    record AIAnalysisResult(
            String summary,
            BigDecimal ambiguityScore,
            BigDecimal completenessScore,
            BigDecimal qualityScore,
            String suggestions
    ) {
    }
}
