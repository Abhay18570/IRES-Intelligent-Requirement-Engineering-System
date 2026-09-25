package com.ires.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.ai.dto.AIAnalysisResponse;
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
import com.ires.ai.dto.analysis.RequirementCandidate;
import com.ires.ai.entity.AnalysisStatus;
import com.ires.ai.entity.RequirementAIAnalysis;
import com.ires.ai.repository.RequirementAIAnalysisRepository;
import com.ires.common.exception.NotFoundException;
import com.ires.common.exception.ServiceUnavailableException;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.repository.RequirementRepository;
import com.ires.requirement.service.RequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequirementAIAnalysisService {

    private final RequirementAIAnalysisRepository analysisRepository;
    private final RequirementService requirementService;
    private final RequirementRepository requirementRepository;
    private final AIAnalysisProvider analysisProvider;
    private final ObjectMapper objectMapper;

    @Transactional
    public AIAnalysisResponse analyze(UUID requirementId, UserDetails principal) {
        Requirement requirement = requirementService.findAccessibleRequirement(requirementId, principal);
        RequirementAIAnalysis analysis = analysisRepository.findByRequirementId(requirementId)
                .orElseGet(() -> new RequirementAIAnalysis(requirement));
        analysis.setAnalysisStatus(AnalysisStatus.PROCESSING);
        analysis = analysisRepository.save(analysis);

        try {
            AIAnalysisProvider.AIAnalysisResult result = analysisProvider.analyze(requirement);
            analysis.setAnalysisStatus(AnalysisStatus.COMPLETED);
            analysis.setSummary(result.summary());
            analysis.setAmbiguityScore(result.ambiguityScore());
            analysis.setCompletenessScore(result.completenessScore());
            analysis.setQualityScore(result.qualityScore());
            analysis.setSuggestions(result.suggestions());
            analysis.setAnalyzedAt(Instant.now());
        } catch (RuntimeException exception) {
            analysis.setAnalysisStatus(AnalysisStatus.FAILED);
            analysis.setSummary("AI analysis could not be completed.");
            analysis.setSuggestions("Retry the analysis when the provider is available.");
            analysis.setAnalyzedAt(Instant.now());
        }

        return AIAnalysisResponse.from(analysisRepository.save(analysis));
    }

    @Transactional(noRollbackFor = ServiceUnavailableException.class)
    public ClassificationResponse classify(UUID requirementId) {
        Requirement requirement = findRequirement(requirementId);
        RequirementAIAnalysis analysis = null;

        try {
            analysis = startAnalysis(requirement);

            ClassificationResponse response = analysisProvider.classify(
                    new ClassificationRequest(requirement.getId(), requirementText(requirement)));

            analysis.setClassificationResult(asJson(response));
            completeAnalysis(analysis);
            return response;
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ServiceUnavailableException(
                    "Classification could not be completed because another AI analysis is already in progress."
            );
        } catch (RuntimeException exception) {
            if (analysis != null) {
                throw analysisFailed(analysis, "Classification", exception);
            }
            throw exception;
        }
    }

    @Transactional(noRollbackFor = ServiceUnavailableException.class)
    public AmbiguityResponse detectAmbiguity(UUID requirementId) {
        Requirement requirement = findRequirement(requirementId);
        RequirementAIAnalysis analysis = null;

        try {
            analysis = startAnalysis(requirement);

            AmbiguityResponse response = analysisProvider.detectAmbiguity(
                    new AmbiguityRequest(requirement.getId(), requirementText(requirement)));

            analysis.setAmbiguityResult(asJson(response));
            completeAnalysis(analysis);
            return response;
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ServiceUnavailableException(
                    "Ambiguity detection could not be completed because another AI analysis is already in progress."
            );
        } catch (RuntimeException exception) {
            if (analysis != null) {
                throw analysisFailed(analysis, "Ambiguity detection", exception);
            }
            throw exception;
        }
    }

    @Transactional(noRollbackFor = ServiceUnavailableException.class)
    public CompletenessResponse analyzeCompleteness(UUID requirementId) {
        Requirement requirement = findRequirement(requirementId);
        RequirementAIAnalysis analysis = null;

        try {
            analysis = startAnalysis(requirement);

            CompletenessResponse response = analysisProvider.analyzeCompleteness(
                    new CompletenessRequest(requirement.getId(), requirementText(requirement)));

            analysis.setCompletenessResult(asJson(response));
            completeAnalysis(analysis);
            return response;
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ServiceUnavailableException(
                    "Completeness analysis could not be completed because another AI analysis is already in progress."
            );
        } catch (RuntimeException exception) {
            if (analysis != null) {
                throw analysisFailed(analysis, "Completeness analysis", exception);
            }
            throw exception;
        }
    }

    @Transactional(noRollbackFor = ServiceUnavailableException.class)
    public QualityAnalysisResponse analyzeQuality(UUID requirementId) {
        Requirement requirement = findRequirement(requirementId);
        RequirementAIAnalysis analysis = null;

        try {
            analysis = startAnalysis(requirement);

            QualityAnalysisResponse response = analysisProvider.analyzeQuality(
                    new QualityAnalysisRequest(requirement.getId(), requirementText(requirement)));

            analysis.setQualityResult(asJson(response));
            completeAnalysis(analysis);
            return response;
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ServiceUnavailableException(
                    "Quality analysis could not be completed because another AI analysis is already in progress."
            );
        } catch (RuntimeException exception) {
            if (analysis != null) {
                throw analysisFailed(analysis, "Quality analysis", exception);
            }
            throw exception;
        }
    }

    @Transactional(noRollbackFor = ServiceUnavailableException.class)
    public DuplicateDetectionResponse detectDuplicates(
            UUID requirementId,
            List<UUID> candidateRequirementIds) {

        Requirement requirement = findRequirement(requirementId);
        List<RequirementCandidate> candidates =
                candidateRequirements(requirementId, candidateRequirementIds);
        RequirementAIAnalysis analysis = null;

        try {
            analysis = startAnalysis(requirement);

            DuplicateDetectionResponse response = analysisProvider.detectDuplicates(
                    new DuplicateDetectionRequest(
                            requirement.getId(),
                            requirementText(requirement),
                            candidates
                    )
            );

            analysis.setDuplicateResult(asJson(response));
            completeAnalysis(analysis);
            return response;

        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ServiceUnavailableException(
                    "Duplicate detection could not be completed because another AI analysis is already in progress."
            );
        } catch (RuntimeException exception) {
            if (analysis != null) {
                throw analysisFailed(analysis, "Duplicate detection", exception);
            }
            throw exception;
        }
    }

    @Transactional(noRollbackFor = ServiceUnavailableException.class)
    public ConflictDetectionResponse detectConflicts(
            UUID requirementId,
            List<UUID> candidateRequirementIds) {

        Requirement requirement = findRequirement(requirementId);
        List<RequirementCandidate> candidates =
                candidateRequirements(requirementId, candidateRequirementIds);
        RequirementAIAnalysis analysis = null;

        try {
            analysis = startAnalysis(requirement);

            ConflictDetectionResponse response = analysisProvider.detectConflicts(
                    new ConflictDetectionRequest(
                            requirement.getId(),
                            requirementText(requirement),
                            candidates
                    )
            );

            analysis.setConflictResult(asJson(response));
            completeAnalysis(analysis);
            return response;

        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ServiceUnavailableException(
                    "Conflict detection could not be completed because another AI analysis is already in progress."
            );
        } catch (RuntimeException exception) {
            if (analysis != null) {
                throw analysisFailed(analysis, "Conflict detection", exception);
            }
            throw exception;
        }
    }

    public AIAnalysisResponse get(UUID requirementId, UserDetails principal) {
        requirementService.findAccessibleRequirement(requirementId, principal);
        return analysisRepository.findByRequirementId(requirementId)
                .map(AIAnalysisResponse::from)
                .orElseThrow(() -> new NotFoundException("AI analysis not found."));
    }

    private Requirement findRequirement(UUID requirementId) {
        return requirementRepository.findById(requirementId)
                .orElseThrow(() -> new NotFoundException("Requirement not found."));
    }

    private RequirementAIAnalysis startAnalysis(Requirement requirement) {
        RequirementAIAnalysis analysis = analysisRepository.findByRequirementId(requirement.getId())
                .orElseGet(() -> new RequirementAIAnalysis(requirement));
        analysis.setAnalysisStatus(AnalysisStatus.PROCESSING);
        return analysisRepository.saveAndFlush(analysis);
    }

    private void completeAnalysis(RequirementAIAnalysis analysis) {
        analysis.setAnalysisStatus(AnalysisStatus.COMPLETED);
        analysis.setAnalyzedAt(Instant.now());
        analysisRepository.save(analysis);
    }

    private ServiceUnavailableException analysisFailed(
            RequirementAIAnalysis analysis, String capability, RuntimeException exception) {
        analysis.setAnalysisStatus(AnalysisStatus.FAILED);
        analysis.setAnalyzedAt(Instant.now());
        analysisRepository.save(analysis);
        return new ServiceUnavailableException(capability + " could not be completed.");
    }

    private JsonNode asJson(Object response) {
        return objectMapper.valueToTree(response);
    }

    private List<RequirementCandidate> candidateRequirements(UUID targetRequirementId, List<UUID> candidateRequirementIds) {
        if (candidateRequirementIds == null || candidateRequirementIds.isEmpty()) {
            return List.of();
        }
        return candidateRequirementIds.stream()
                .filter(candidateRequirementId -> !targetRequirementId.equals(candidateRequirementId))
                .map(this::findRequirement)
                .map(candidate -> new RequirementCandidate(candidate.getId(), requirementText(candidate)))
                .toList();
    }

    private String requirementText(Requirement requirement) {
        String description = requirement.getDescription();
        return description == null || description.isBlank() ? requirement.getTitle() : description;
    }
}
