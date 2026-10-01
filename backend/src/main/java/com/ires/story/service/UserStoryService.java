package com.ires.story.service;

import com.ires.ai.service.AIAnalysisProvider;
import com.ires.common.exception.ForbiddenException;
import com.ires.common.exception.ConflictException;
import com.ires.common.exception.NotFoundException;
import com.ires.common.exception.ServiceUnavailableException;
import com.ires.project.service.ProjectService;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.service.RequirementService;
import com.ires.story.dto.GeneratedUserStory;
import com.ires.story.dto.UserStoryCreateRequest;
import com.ires.story.dto.UserStoryResponse;
import com.ires.story.dto.UserStoryUpdateRequest;
import com.ires.story.entity.StoryStatus;
import com.ires.story.entity.UserStory;
import com.ires.story.repository.UserStoryRepository;
import com.ires.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserStoryService {

    private final UserStoryRepository userStoryRepository;
    private final RequirementService requirementService;
    private final ProjectService projectService;
    private final AIAnalysisProvider analysisProvider;

    @Transactional
    public UserStoryResponse create(UUID requirementId, UserStoryCreateRequest request, UserDetails principal) {
        Requirement requirement = requirementService.findAccessibleRequirement(requirementId, principal);
        User creator = projectService.currentUser(principal);
        UserStory story = new UserStory(
                requirement,
                request.title().trim(),
                request.description(),
                request.storyText().trim(),
                defaultPriority(request.priority(), requirement),
                defaultStatus(request.status()),
                creator
        );
        return UserStoryResponse.from(userStoryRepository.save(story));
    }

    public Page<UserStoryResponse> list(UUID requirementId, Pageable pageable, UserDetails principal) {
        requirementService.findAccessibleRequirement(requirementId, principal);
        return userStoryRepository.findByRequirementId(requirementId, pageable).map(UserStoryResponse::from);
    }

    public UserStoryResponse get(UUID id, UserDetails principal) {
        UserStory story = findStory(id);
        requirementService.findAccessibleRequirement(story.getRequirement().getId(), principal);
        return UserStoryResponse.from(story);
    }

    @Transactional
    public UserStoryResponse update(UUID id, UserStoryUpdateRequest request, UserDetails principal) {
        UserStory story = findStory(id);
        assertCanModify(story, principal);
        assertNotReviewManaged(story);
        story.setTitle(request.title().trim());
        story.setDescription(request.description());
        story.setStoryText(request.storyText().trim());
        story.setPriority(defaultPriority(request.priority(), story.getRequirement()));
        story.setStatus(defaultStatus(request.status()));
        return UserStoryResponse.from(story);
    }

    @Transactional
    public void delete(UUID id, UserDetails principal) {
        UserStory story = findStory(id);
        assertCanModify(story, principal);
        assertNotReviewManaged(story);
        userStoryRepository.delete(story);
    }

    @Transactional
    public UserStoryResponse generate(UUID requirementId, UserDetails principal) {
        Requirement requirement = requirementService.findAccessibleRequirement(requirementId, principal);
        User creator = projectService.currentUser(principal);
        GeneratedUserStory generated;
        try {
            generated = analysisProvider.generateUserStory(requirement);
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("The AI provider is currently unavailable.");
        }

        UserStory story = new UserStory(
                requirement,
                generated.title(),
                generated.description(),
                generated.storyText(),
                generated.priority(),
                StoryStatus.PENDING_REVIEW,
                creator
        );
        return UserStoryResponse.from(userStoryRepository.save(story));
    }

    private UserStory findStory(UUID id) {
        return userStoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User story not found."));
    }

    private void assertCanModify(UserStory story, UserDetails principal) {
        if (isAdmin(principal)) {
            return;
        }
        User currentUser = projectService.currentUser(principal);
        boolean creator = story.getCreatedBy().getId().equals(currentUser.getId());
        boolean projectOwner = story.getRequirement().getProject().getClient().getId().equals(currentUser.getId());
        if (!creator && !projectOwner) {
            throw new ForbiddenException("Only the story creator, project owner, or an admin can modify this story.");
        }
        requirementService.findAccessibleRequirement(story.getRequirement().getId(), principal);
    }

    private boolean isAdmin(UserDetails principal) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private void assertNotReviewManaged(UserStory story) {
        if (story.getStatus() == StoryStatus.PENDING_REVIEW
                || story.getStatus() == StoryStatus.APPROVED
                || story.getStatus() == StoryStatus.REJECTED) {
            throw new ConflictException("Reviewed artifacts can only be changed through the review workflow.");
        }
    }

    private RequirementPriority defaultPriority(RequirementPriority priority, Requirement requirement) {
        return priority == null ? requirement.getPriority() : priority;
    }

    private StoryStatus defaultStatus(StoryStatus status) {
        if (status == StoryStatus.PENDING_REVIEW || status == StoryStatus.APPROVED || status == StoryStatus.REJECTED) {
            throw new ForbiddenException("Review status can only be changed through the artifact review endpoint.");
        }
        return status == null ? StoryStatus.DRAFT : status;
    }
}
