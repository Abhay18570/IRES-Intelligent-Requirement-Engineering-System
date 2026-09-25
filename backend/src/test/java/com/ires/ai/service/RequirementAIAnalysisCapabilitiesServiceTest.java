package com.ires.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.ai.dto.analysis.AmbiguityFinding;
import com.ires.ai.dto.analysis.AmbiguityResponse;
import com.ires.ai.dto.analysis.ClassificationRequest;
import com.ires.ai.dto.analysis.ClassificationResponse;
import com.ires.ai.dto.analysis.CompletenessResponse;
import com.ires.ai.dto.analysis.ConflictDetectionRequest;
import com.ires.ai.dto.analysis.ConflictDetectionResponse;
import com.ires.ai.dto.analysis.ConflictFinding;
import com.ires.ai.dto.analysis.DuplicateCandidate;
import com.ires.ai.dto.analysis.DuplicateDetectionRequest;
import com.ires.ai.dto.analysis.DuplicateDetectionResponse;
import com.ires.ai.dto.analysis.MissingInformation;
import com.ires.ai.dto.analysis.QualityAnalysisResponse;
import com.ires.ai.dto.analysis.QualityDimension;
import com.ires.ai.dto.analysis.RequirementCandidate;
import com.ires.ai.entity.AnalysisStatus;
import com.ires.ai.entity.RequirementAIAnalysis;
import com.ires.ai.repository.RequirementAIAnalysisRepository;
import com.ires.common.exception.NotFoundException;
import com.ires.common.exception.ServiceUnavailableException;
import com.ires.project.entity.Project;
import com.ires.project.entity.ProjectStatus;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementStatus;
import com.ires.requirement.entity.RequirementType;
import com.ires.requirement.repository.RequirementRepository;
import com.ires.requirement.service.RequirementService;
import com.ires.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementAIAnalysisCapabilitiesServiceTest {

    @Mock
    private RequirementAIAnalysisRepository analysisRepository;

    @Mock
    private RequirementService requirementService;

    @Mock
    private RequirementRepository requirementRepository;

    @Mock
    private AIAnalysisProvider analysisProvider;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RequirementAIAnalysisService analysisService;

    private Requirement requirement;
    private RequirementAIAnalysis analysis;

    @BeforeEach
    void setUp() {
        requirement = requirement("Target requirement", "Target details");
        analysis = new RequirementAIAnalysis(requirement);
        }

    @Test
    void persistsEachCapabilityResponseToItsMatchingJsonField() {
        when(requirementRepository.findById(requirement.getId())).thenReturn(Optional.of(requirement));
        when(analysisRepository.findByRequirementId(requirement.getId())).thenReturn(Optional.of(analysis));
        when(analysisRepository.saveAndFlush(any(RequirementAIAnalysis.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

                when(analysisRepository.save(any(RequirementAIAnalysis.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));
        UUID candidateId = UUID.randomUUID();
        Requirement candidate = requirement("Candidate requirement", "Candidate details");
        candidate.setId(candidateId);
        when(requirementRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

        when(analysisProvider.classify(any())).thenReturn(
                new ClassificationResponse("FUNCTIONAL", new BigDecimal("0.94"), "Behavior is specified."));
        when(analysisProvider.detectAmbiguity(any())).thenReturn(
                new AmbiguityResponse(true, List.of(new AmbiguityFinding("fast", "Not measurable", "Specify a limit")),
                        new BigDecimal("0.89")));
        when(analysisProvider.analyzeCompleteness(any())).thenReturn(
                new CompletenessResponse(false, List.of(new MissingInformation("Errors", "Failure behavior missing")),
                        List.of("What happens on failure?"), new BigDecimal("0.86")));
        when(analysisProvider.analyzeQuality(any())).thenReturn(
                new QualityAnalysisResponse(82, List.of(new QualityDimension("CLARITY", 82, "Clear", "Keep precise")),
                        new BigDecimal("0.91")));
        when(analysisProvider.detectDuplicates(any())).thenReturn(
                new DuplicateDetectionResponse(List.of(new DuplicateCandidate(candidateId, new BigDecimal("0.88"),
                        "SIMILAR", "Overlapping behavior")), new BigDecimal("0.90")));
        when(analysisProvider.detectConflicts(any())).thenReturn(
                new ConflictDetectionResponse(List.of(new ConflictFinding(candidateId, "LOGICAL", "MEDIUM",
                        "Constraints conflict", "Align rules")), new BigDecimal("0.87")));

        analysisService.classify(requirement.getId());
        analysisService.detectAmbiguity(requirement.getId());
        analysisService.analyzeCompleteness(requirement.getId());
        analysisService.analyzeQuality(requirement.getId());
        analysisService.detectDuplicates(requirement.getId(), List.of(candidateId));
        analysisService.detectConflicts(requirement.getId(), List.of(candidateId));

        assertThat(analysis.getClassificationResult().path("classification").asText()).isEqualTo("FUNCTIONAL");
        assertThat(analysis.getAmbiguityResult().path("hasAmbiguity").asBoolean()).isTrue();
        assertThat(analysis.getCompletenessResult().path("isComplete").asBoolean()).isFalse();
        assertThat(analysis.getQualityResult().path("overallScore").asInt()).isEqualTo(82);
        assertThat(analysis.getDuplicateResult().path("duplicates").get(0).path("requirementId").asText())
                .isEqualTo(candidateId.toString());
        assertThat(analysis.getConflictResult().path("conflicts").get(0).path("requirementId").asText())
                .isEqualTo(candidateId.toString());
        assertThat(analysis.getAnalysisStatus()).isEqualTo(AnalysisStatus.COMPLETED);

        verify(analysisProvider).classify(new ClassificationRequest(requirement.getId(), "Target details"));
        verify(analysisProvider).detectDuplicates(new DuplicateDetectionRequest(requirement.getId(), "Target details",
                List.of(new RequirementCandidate(candidateId, "Candidate details"))));
        verify(analysisProvider).detectConflicts(new ConflictDetectionRequest(requirement.getId(), "Target details",
                List.of(new RequirementCandidate(candidateId, "Candidate details"))));
    }

    @Test
    void providerFailurePersistsFailedStatusAndPropagatesServiceUnavailable() {
        when(requirementRepository.findById(requirement.getId())).thenReturn(Optional.of(requirement));
        when(analysisRepository.findByRequirementId(requirement.getId())).thenReturn(Optional.of(analysis));
        when(analysisRepository.saveAndFlush(any(RequirementAIAnalysis.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

        when(analysisRepository.save(any(RequirementAIAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisProvider.classify(any())).thenThrow(new IllegalStateException("offline"));

        assertThatThrownBy(() -> analysisService.classify(requirement.getId()))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Classification could not be completed.");

        assertThat(analysis.getAnalysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getAnalyzedAt()).isNotNull();
        verify(analysisProvider).classify(any());
    }

    @Test
    void missingTargetDoesNotInvokeProviderOrCreateAnalysis() {
        UUID missingId = UUID.randomUUID();
        when(requirementRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.classify(missingId))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(analysisProvider, analysisRepository);
    }

    @Test
        void missingCandidateDoesNotInvokeProviderOrStartAnalysis() {
        UUID missingCandidateId = UUID.randomUUID();

        when(requirementRepository.findById(requirement.getId()))
                .thenReturn(Optional.of(requirement));
        when(requirementRepository.findById(missingCandidateId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                analysisService.detectDuplicates(
                        requirement.getId(),
                        List.of(missingCandidateId)))
                .isInstanceOf(NotFoundException.class);

        verify(analysisProvider, never()).detectDuplicates(any());
        verifyNoInteractions(analysisRepository);
        }

    @Test
    void emptyAndNullCandidateListsReachProviderAsEmptyCandidatesAndTargetIsExcluded() {
        when(requirementRepository.findById(requirement.getId())).thenReturn(Optional.of(requirement));
        when(analysisRepository.findByRequirementId(requirement.getId())).thenReturn(Optional.of(analysis));
        when(analysisRepository.saveAndFlush(any(RequirementAIAnalysis.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

when(analysisRepository.save(any(RequirementAIAnalysis.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisProvider.detectDuplicates(any())).thenReturn(
                new DuplicateDetectionResponse(List.of(), new BigDecimal("0.90")));
        when(analysisProvider.detectConflicts(any())).thenReturn(
                new ConflictDetectionResponse(List.of(), new BigDecimal("0.87")));

        analysisService.detectDuplicates(requirement.getId(), null);
        analysisService.detectConflicts(requirement.getId(), List.of(requirement.getId()));

        ArgumentCaptor<DuplicateDetectionRequest> duplicateRequest = ArgumentCaptor.forClass(DuplicateDetectionRequest.class);
        ArgumentCaptor<ConflictDetectionRequest> conflictRequest = ArgumentCaptor.forClass(ConflictDetectionRequest.class);
        verify(analysisProvider).detectDuplicates(duplicateRequest.capture());
        verify(analysisProvider).detectConflicts(conflictRequest.capture());
        assertThat(duplicateRequest.getValue().candidateRequirements()).isEmpty();
        assertThat(conflictRequest.getValue().candidateRequirements()).isEmpty();
    }

    @Test
    void classifyFallsBackToTitleWhenDescriptionIsBlankOrNull() {
        Requirement titleOnlyRequirement = requirement("Title only requirement", "   ");
        RequirementAIAnalysis titleAnalysis = new RequirementAIAnalysis(titleOnlyRequirement);

        when(requirementRepository.findById(titleOnlyRequirement.getId())).thenReturn(Optional.of(titleOnlyRequirement));
        when(analysisRepository.findByRequirementId(titleOnlyRequirement.getId())).thenReturn(Optional.of(titleAnalysis));
        when(analysisRepository.saveAndFlush(any(RequirementAIAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisRepository.save(any(RequirementAIAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisProvider.classify(any())).thenReturn(
                new ClassificationResponse("NON_FUNCTIONAL", new BigDecimal("0.92"), "Non-functional rule."));

        ClassificationResponse response = analysisService.classify(titleOnlyRequirement.getId());

        assertThat(response.classification()).isEqualTo("NON_FUNCTIONAL");
        assertThat(titleAnalysis.getClassificationResult().path("classification").asText()).isEqualTo("NON_FUNCTIONAL");
        assertThat(titleAnalysis.getAnalysisStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        verify(analysisProvider).classify(new ClassificationRequest(titleOnlyRequirement.getId(), "Title only requirement"));
    }

    @Test
    void classifyWithJevProviderIntegratesAndPersistsClassificationResult() throws Exception {
        com.ires.ai.provider.jev.JevApiClient jevApiClient = org.mockito.Mockito.mock(com.ires.ai.provider.jev.JevApiClient.class);
        com.ires.ai.provider.jev.JevAIAnalysisProvider jevProvider =
                new com.ires.ai.provider.jev.JevAIAnalysisProvider(jevApiClient, "typesafe-ai/jev");
        RequirementAIAnalysisService jevIntegratedService = new RequirementAIAnalysisService(
                analysisRepository,
                requirementService,
                requirementRepository,
                jevProvider,
                objectMapper
        );

        when(requirementRepository.findById(requirement.getId())).thenReturn(Optional.of(requirement));
        when(analysisRepository.findByRequirementId(requirement.getId())).thenReturn(Optional.of(analysis));
        when(analysisRepository.saveAndFlush(any(RequirementAIAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisRepository.save(any(RequirementAIAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        com.fasterxml.jackson.databind.JsonNode jevData = objectMapper.readTree("""
                {
                  "answers": {
                    "classification": {
                      "choice": "BUSINESS",
                      "confidence": 0.95
                    }
                  }
                }
                """);

        when(jevApiClient.decide(any(com.ires.ai.provider.jev.dto.JevDecisionRequest.class)))
                .thenReturn(new com.ires.ai.provider.jev.dto.JevDecisionResponse(
                        0,
                        "Decision completed",
                        jevData
                ));

        ClassificationResponse response = jevIntegratedService.classify(requirement.getId());

        assertThat(response.classification()).isEqualTo("BUSINESS");
        assertThat(response.confidence()).isEqualTo(new BigDecimal("0.95"));
        assertThat(analysis.getClassificationResult().path("classification").asText()).isEqualTo("BUSINESS");
        assertThat(analysis.getAnalysisStatus()).isEqualTo(AnalysisStatus.COMPLETED);

        verify(jevApiClient).decide(org.mockito.ArgumentMatchers.argThat(req ->
                "typesafe-ai/jev".equals(req.model())
                        && req.questions().containsKey("classification")
        ));
    }

    private Requirement requirement(String title, String description) {
        User owner = new User("Test", "User", "owner.com", "hash", null);
        owner.setId(UUID.randomUUID());
        Project project = new Project("Checkout", "Revamp", ProjectStatus.ACTIVE, null, null, owner);
        Requirement value = new Requirement(project, title, description, RequirementType.FUNCTIONAL,
                RequirementPriority.MEDIUM, RequirementStatus.DRAFT, "client", owner, null);
        value.setId(UUID.randomUUID());
        return value;
    }
}

