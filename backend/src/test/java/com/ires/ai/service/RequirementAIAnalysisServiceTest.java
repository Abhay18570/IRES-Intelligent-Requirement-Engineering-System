package com.ires.ai.service;

import com.ires.ai.entity.AnalysisStatus;
import com.ires.ai.entity.RequirementAIAnalysis;
import com.ires.ai.repository.RequirementAIAnalysisRepository;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementStatus;
import com.ires.requirement.entity.RequirementType;
import com.ires.requirement.service.RequirementService;
import com.ires.project.entity.Project;
import com.ires.project.entity.ProjectStatus;
import com.ires.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementAIAnalysisServiceTest {

    @Mock
    private RequirementAIAnalysisRepository analysisRepository;

    @Mock
    private RequirementService requirementService;

    @Mock
    private AIAnalysisProvider analysisProvider;

    @Mock
    private UserDetails principal;

    @InjectMocks
    private RequirementAIAnalysisService analysisService;

    @Test
    void successfulAnalysisIsPersistedAsCompleted() {
        Requirement requirement = requirement();
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(analysisRepository.findByRequirementId(requirement.getId())).thenReturn(Optional.empty());

        when(analysisRepository.save(any(RequirementAIAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(analysisProvider.analyze(requirement)).thenReturn(new AIAnalysisProvider.AIAnalysisResult(
                "Summary", new BigDecimal("10"), new BigDecimal("90"), new BigDecimal("85"), "Suggestion"));
        var response = analysisService.analyze(requirement.getId(), principal);

        assertThat(response.analysisStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(response.summary()).isEqualTo("Summary");
        assertThat(response.qualityScore()).isEqualByComparingTo("85");
    }

    @Test
    void unavailableProviderIsPersistedAsFailed() {
        Requirement requirement = requirement();
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(analysisRepository.findByRequirementId(requirement.getId())).thenReturn(Optional.empty());

        when(analysisRepository.save(any(RequirementAIAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(analysisProvider.analyze(requirement)).thenThrow(new IllegalStateException("provider unavailable"));

        var response = analysisService.analyze(requirement.getId(), principal);

        assertThat(response.analysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(response.suggestions()).contains("Retry");
    }

    @Test
    void missingAnalysisRequirementIsRejectedByAccessBoundary() {
        UUID requirementId = UUID.randomUUID();
        when(requirementService.findAccessibleRequirement(requirementId, principal))
                .thenThrow(new com.ires.common.exception.NotFoundException("Requirement not found."));

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> analysisService.analyze(requirementId, principal)))
                .isInstanceOf(com.ires.common.exception.NotFoundException.class);
    }

    private Requirement requirement() {
        User owner = new User("Test", "User", "owner@example.com", "hash", null);
        owner.setId(UUID.randomUUID());
        Project project = new Project("Checkout", "Revamp", ProjectStatus.ACTIVE, null, null, owner);
        Requirement requirement = new Requirement(project, "Guest checkout", "Details",
                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM, RequirementStatus.DRAFT,
                "client", owner, null);
        requirement.setId(UUID.randomUUID());
        return requirement;
    }
}
