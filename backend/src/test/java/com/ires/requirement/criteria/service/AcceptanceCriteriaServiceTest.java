package com.ires.requirement.criteria.service;

import com.ires.common.exception.BadRequestException;
import com.ires.common.exception.NotFoundException;
import com.ires.common.exception.ServiceUnavailableException;
import com.ires.common.exception.ForbiddenException;
import com.ires.ai.dto.analysis.AcceptanceCriteriaGenerationResponse;
import com.ires.ai.service.AIAnalysisProvider;
import com.ires.project.entity.Project;
import com.ires.project.entity.ProjectStatus;
import com.ires.project.service.ProjectService;
import com.ires.requirement.criteria.dto.AcceptanceCriteriaCreateRequest;
import com.ires.requirement.criteria.entity.AcceptanceCriteria;
import com.ires.requirement.criteria.entity.CriteriaStatus;
import com.ires.requirement.criteria.entity.CriteriaType;
import com.ires.requirement.criteria.repository.AcceptanceCriteriaRepository;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementStatus;
import com.ires.requirement.entity.RequirementType;
import com.ires.requirement.service.RequirementService;
import com.ires.story.entity.StoryStatus;
import com.ires.story.entity.UserStory;
import com.ires.story.repository.UserStoryRepository;
import com.ires.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AcceptanceCriteriaServiceTest {

    @Mock
    private AcceptanceCriteriaRepository criteriaRepository;

    @Mock
    private RequirementService requirementService;

    @Mock
    private ProjectService projectService;

    @Mock
    private UserStoryRepository userStoryRepository;

        @Mock
        private AIAnalysisProvider analysisProvider;

    @Mock
    private UserDetails principal;

    @InjectMocks
    private AcceptanceCriteriaService criteriaService;

    @BeforeEach
    void businessAnalystPrincipal() {
        doReturn(java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_BUSINESS_ANALYST")))
                .when(principal).getAuthorities();
    }

    @Test
    void createsCriteriaLinkedToAnOptionalStory() {
        Requirement requirement = requirement();
        UserStory story = new UserStory(requirement, "Checkout story", "Description", "As a shopper...",
                RequirementPriority.HIGH, StoryStatus.READY, requirement.getCreatedBy());
        story.setId(UUID.randomUUID());
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(userStoryRepository.findById(story.getId())).thenReturn(Optional.of(story));
        when(criteriaRepository.save(any(AcceptanceCriteria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = criteriaService.create(requirement.getId(), new AcceptanceCriteriaCreateRequest(
                story.getId(), "Checkout succeeds", "Payment is accepted.", CriteriaType.BEHAVIORAL,
                CriteriaStatus.READY), principal);

        assertThat(response.requirementId()).isEqualTo(requirement.getId());
        assertThat(response.userStoryId()).isEqualTo(story.getId());
        assertThat(response.criteriaType()).isEqualTo(CriteriaType.BEHAVIORAL);
    }

    @Test
    void rejectsStoryFromAnotherRequirement() {
        Requirement requirement = requirement();
        Requirement otherRequirement = requirement();
        UserStory story = new UserStory(otherRequirement, "Other story", "Description", "As a user...",
                RequirementPriority.MEDIUM, StoryStatus.DRAFT, otherRequirement.getCreatedBy());
        UUID storyId = UUID.randomUUID();
        story.setId(storyId);
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(userStoryRepository.findById(storyId)).thenReturn(Optional.of(story));

        assertThatThrownBy(() -> criteriaService.create(requirement.getId(), new AcceptanceCriteriaCreateRequest(
                storyId, "Criterion", null, null, null), principal))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsMissingStory() {
        Requirement requirement = requirement();
        UUID storyId = UUID.randomUUID();
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(userStoryRepository.findById(storyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> criteriaService.create(requirement.getId(), new AcceptanceCriteriaCreateRequest(
                storyId, "Criterion", null, null, null), principal))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void generatesAndAppendsCriteriaForRequirementAndStory() {
        Requirement requirement = requirement();
        UserStory story = new UserStory(requirement, "Checkout story", "Shopper checks out",
                "As a shopper, I want guest checkout.", RequirementPriority.MEDIUM,
                StoryStatus.DRAFT, requirement.getCreatedBy());
        story.setId(UUID.randomUUID());
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(userStoryRepository.findById(story.getId())).thenReturn(Optional.of(story));
        when(analysisProvider.generateAcceptanceCriteria(requirement, story)).thenReturn(
                new AcceptanceCriteriaGenerationResponse(java.util.List.of(
                        new AcceptanceCriteriaGenerationResponse.GeneratedCriterion(
                                "Checkout succeeds", "Given valid details, when submitted, then checkout succeeds.",
                                CriteriaType.BEHAVIORAL),
                        new AcceptanceCriteriaGenerationResponse.GeneratedCriterion(
                                "Invalid details are rejected", "Given invalid details, when submitted, then checkout is rejected.",
                                CriteriaType.VALIDATION)
                )));
        when(criteriaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var responses = criteriaService.generate(requirement.getId(), story.getId(), principal);

        assertThat(responses).hasSize(2);
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.requirementId()).isEqualTo(requirement.getId());
            assertThat(response.userStoryId()).isEqualTo(story.getId());
            assertThat(response.status()).isEqualTo(CriteriaStatus.PENDING_REVIEW);
        });
        assertThat(responses.get(0).title()).isEqualTo("Checkout succeeds");
        verify(analysisProvider).generateAcceptanceCriteria(requirement, story);
        verify(criteriaRepository).saveAll(any());
        verify(criteriaRepository, never()).delete(any(AcceptanceCriteria.class));
    }

    @Test
    void doesNotPersistWhenProviderReturnsNoCriteria() {
        Requirement requirement = requirement();
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(analysisProvider.generateAcceptanceCriteria(requirement, null)).thenReturn(
                new AcceptanceCriteriaGenerationResponse(java.util.List.of()));

        assertThatThrownBy(() -> criteriaService.generate(requirement.getId(), null, principal))
                .isInstanceOf(ServiceUnavailableException.class);
        verify(criteriaRepository, never()).saveAll(any());
    }

        @Test
        void manualCriteriaCreationCannotSetReviewOnlyStatus() {
                Requirement requirement = requirement();
                when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);

                assertThatThrownBy(() -> criteriaService.create(requirement.getId(), new AcceptanceCriteriaCreateRequest(
                                null, "Manually approved", "Description", CriteriaType.FUNCTIONAL, CriteriaStatus.APPROVED), principal))
                                .isInstanceOf(ForbiddenException.class);
        }

                    @Test
                    void rejectedCriteriaCannotBeUpdatedOrDeletedOutsideReviewWorkflow() {
                        Requirement requirement = requirement();
                        AcceptanceCriteria criteria = new AcceptanceCriteria(requirement, null, "Rejected criterion", "Details",
                                CriteriaType.FUNCTIONAL, CriteriaStatus.REJECTED);
                        UUID criteriaId = UUID.randomUUID();
                        criteria.setId(criteriaId);
                        when(criteriaRepository.findById(criteriaId)).thenReturn(Optional.of(criteria));
                        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);

                        assertThatThrownBy(() -> criteriaService.update(criteriaId,
                                new com.ires.requirement.criteria.dto.AcceptanceCriteriaUpdateRequest(
                                        null, "Changed", "Changed details", CriteriaType.FUNCTIONAL, null), principal))
                                .isInstanceOf(com.ires.common.exception.ConflictException.class);
                        assertThatThrownBy(() -> criteriaService.delete(criteriaId, principal))
                                .isInstanceOf(com.ires.common.exception.ConflictException.class);
                        verify(criteriaRepository, never()).delete(criteria);
                    }

    private Requirement requirement() {
        User owner = new User("Test", "Owner", "owner@example.com", "hash", null);
        owner.setId(UUID.randomUUID());
        Project project = new Project("Checkout", "Revamp", ProjectStatus.ACTIVE, null, null, owner);
        Requirement requirement = new Requirement(project, "Guest checkout", "Details",
                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM, RequirementStatus.ANALYSIS_COMPLETED,
                "client", owner, null);
        requirement.setId(UUID.randomUUID());
        return requirement;
    }
}
