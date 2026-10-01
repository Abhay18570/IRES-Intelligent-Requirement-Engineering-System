package com.ires.story.service;

import com.ires.ai.service.AIAnalysisProvider;
import com.ires.common.exception.ServiceUnavailableException;
import com.ires.common.exception.ForbiddenException;
import com.ires.common.exception.ConflictException;
import com.ires.project.entity.Project;
import com.ires.project.entity.ProjectStatus;
import com.ires.project.service.ProjectService;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementStatus;
import com.ires.requirement.entity.RequirementType;
import com.ires.requirement.service.RequirementService;
import com.ires.story.dto.GeneratedUserStory;
import com.ires.story.dto.UserStoryCreateRequest;
import com.ires.story.dto.UserStoryUpdateRequest;
import com.ires.story.entity.StoryStatus;
import com.ires.story.entity.UserStory;
import com.ires.story.repository.UserStoryRepository;
import com.ires.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserStoryServiceTest {

    @Mock
    private UserStoryRepository userStoryRepository;

    @Mock
    private RequirementService requirementService;

    @Mock
    private ProjectService projectService;

    @Mock
    private AIAnalysisProvider analysisProvider;

    @Mock
    private UserDetails principal;

    @InjectMocks
    private UserStoryService userStoryService;

    @Test
    void createsManualStoryLinkedToRequirement() {
        Requirement requirement = requirement();
        User creator = requirement.getCreatedBy();
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(projectService.currentUser(principal)).thenReturn(creator);
        when(userStoryRepository.save(any(UserStory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userStoryService.create(requirement.getId(), new UserStoryCreateRequest(
                "Guest checkout story", "Description", "As a shopper, I want guest checkout.",
                RequirementPriority.HIGH, StoryStatus.READY), principal);

        assertThat(response.requirementId()).isEqualTo(requirement.getId());
        assertThat(response.status()).isEqualTo(StoryStatus.READY);
        assertThat(response.createdBy().email()).isEqualTo("creator@example.com");
    }

    @Test
    void generatesStoryThroughAIProvider() {
        Requirement requirement = requirement();
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(projectService.currentUser(principal)).thenReturn(requirement.getCreatedBy());
        when(analysisProvider.generateUserStory(requirement)).thenReturn(new GeneratedUserStory(
            "Guest checkout story",
            "A shopper can check out without an account.",
            "As a shopper, I want to check out as a guest so that I can purchase without creating an account.",
            RequirementPriority.MEDIUM
        ));
        when(userStoryRepository.save(any(UserStory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = userStoryService.generate(requirement.getId(), principal);

        assertThat(response.title()).isEqualTo("Guest checkout story");
        assertThat(response.description()).isEqualTo("A shopper can check out without an account.");
        assertThat(response.storyText()).isEqualTo(
            "As a shopper, I want to check out as a guest so that I can purchase without creating an account.");
        assertThat(response.priority()).isEqualTo(RequirementPriority.MEDIUM);
        assertThat(response.status()).isEqualTo(StoryStatus.PENDING_REVIEW);
    }

    @Test
    void reportsUnavailableAIProviderAsServiceUnavailable() {
        Requirement requirement = requirement();
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(analysisProvider.generateUserStory(requirement)).thenThrow(new IllegalStateException("offline"));

        assertThatThrownBy(() -> userStoryService.generate(requirement.getId(), principal))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void manualStoryCreationCannotSetReviewOnlyStatus() {
        Requirement requirement = requirement();
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(projectService.currentUser(principal)).thenReturn(requirement.getCreatedBy());

        assertThatThrownBy(() -> userStoryService.create(requirement.getId(), new UserStoryCreateRequest(
                "Manually approved", "Description", "As a user, I want a feature.",
                RequirementPriority.MEDIUM, StoryStatus.APPROVED), principal))
                .isInstanceOf(ForbiddenException.class);
    }

            @Test
            void pendingStoryCannotBeUpdatedOrDeletedOutsideReviewWorkflow() {
            Requirement requirement = requirement();
            UserStory story = new UserStory(requirement, "Guest checkout", "Description",
                "As a shopper, I want guest checkout.", RequirementPriority.MEDIUM,
                StoryStatus.PENDING_REVIEW, requirement.getCreatedBy());
            story.setId(UUID.randomUUID());
            when(userStoryRepository.findById(story.getId())).thenReturn(Optional.of(story));
            when(projectService.currentUser(principal)).thenReturn(requirement.getCreatedBy());
            when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);

            assertThatThrownBy(() -> userStoryService.update(story.getId(), new UserStoryUpdateRequest(
                "Edited title", "Edited description", "As a shopper, I want guest checkout.",
                RequirementPriority.MEDIUM, null), principal))
                .isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> userStoryService.delete(story.getId(), principal))
                .isInstanceOf(ConflictException.class);
            org.mockito.Mockito.verify(userStoryRepository, org.mockito.Mockito.never()).delete(story);
            }

    @Test
    void missingRequirementIsRejected() {
        UUID requirementId = UUID.randomUUID();
        when(requirementService.findAccessibleRequirement(requirementId, principal))
                .thenThrow(new com.ires.common.exception.NotFoundException("Requirement not found."));

        assertThatThrownBy(() -> userStoryService.generate(requirementId, principal))
                .isInstanceOf(com.ires.common.exception.NotFoundException.class);
    }

    private Requirement requirement() {
        User creator = new User("Test", "Creator", "creator@example.com", "hash", null);
        creator.setId(UUID.randomUUID());
        Project project = new Project("Checkout", "Revamp", ProjectStatus.ACTIVE, null, null, creator);
        Requirement requirement = new Requirement(project, "Guest checkout", "Allow guest checkout.",
                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM, RequirementStatus.ANALYSIS_COMPLETED,
                "client", creator, null);
        requirement.setId(UUID.randomUUID());
        return requirement;
    }
}
