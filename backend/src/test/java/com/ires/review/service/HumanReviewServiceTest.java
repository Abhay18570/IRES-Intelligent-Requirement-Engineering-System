package com.ires.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.common.exception.BadRequestException;
import com.ires.common.exception.ConflictException;
import com.ires.common.exception.ForbiddenException;
import com.ires.project.entity.Project;
import com.ires.project.entity.ProjectStatus;
import com.ires.project.service.ProjectService;
import com.ires.requirement.criteria.entity.AcceptanceCriteria;
import com.ires.requirement.criteria.entity.CriteriaStatus;
import com.ires.requirement.criteria.entity.CriteriaType;
import com.ires.requirement.criteria.repository.AcceptanceCriteriaRepository;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementStatus;
import com.ires.requirement.entity.RequirementType;
import com.ires.requirement.service.RequirementService;
import com.ires.review.dto.HumanReviewRequest;
import com.ires.review.entity.HumanReview;
import com.ires.review.entity.ReviewAction;
import com.ires.review.entity.ReviewArtifactType;
import com.ires.review.repository.HumanReviewRepository;
import com.ires.srs.entity.SrsDocument;
import com.ires.srs.entity.SrsDocumentStatus;
import com.ires.srs.repository.SrsDocumentRepository;
import com.ires.story.entity.StoryStatus;
import com.ires.story.entity.UserStory;
import com.ires.story.repository.UserStoryRepository;
import com.ires.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

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
class HumanReviewServiceTest {

    @Mock private UserStoryRepository userStoryRepository;
    @Mock private AcceptanceCriteriaRepository criteriaRepository;
    @Mock private SrsDocumentRepository srsDocumentRepository;
    @Mock private HumanReviewRepository reviewRepository;
    @Mock private RequirementService requirementService;
    @Mock private ProjectService projectService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private UserDetails principal;

    @InjectMocks
    private HumanReviewService service;

    private User reviewer;

    private void stubAnalystReviewer() {
        reviewer = new User("Review", "User", "reviewer@example.com", "hash", null);
        reviewer.setId(UUID.randomUUID());
        org.mockito.Mockito.doReturn(List.of(
            new SimpleGrantedAuthority("ROLE_BUSINESS_ANALYST"))).when(principal).getAuthorities();
        when(projectService.currentUser(principal)).thenReturn(reviewer);
    }

    private void stubReviewPersistence() {
        when(reviewRepository.save(any(HumanReview.class))).thenAnswer(invocation -> {
            HumanReview review = invocation.getArgument(0);
            review.setId(UUID.randomUUID());
            return review;
        });
    }

    @Test
    void acceptsPendingStoryAndPersistsAuditSnapshot() {
        stubAnalystReviewer();
        stubReviewPersistence();
        Requirement requirement = requirement();
        UserStory story = story(requirement, StoryStatus.PENDING_REVIEW);
        when(userStoryRepository.findById(story.getId())).thenReturn(Optional.of(story));
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(userStoryRepository.save(story)).thenReturn(story);

        var result = service.review(ReviewArtifactType.USER_STORY, story.getId(),
                new HumanReviewRequest(ReviewAction.ACCEPT, null, null), principal);

        assertThat(story.getStatus()).isEqualTo(StoryStatus.APPROVED);
        assertThat(story.getStoryText()).isEqualTo("As a shopper, I want guest checkout.");
        assertThat(result.action()).isEqualTo(ReviewAction.ACCEPT);
        assertThat(result.artifactStatus()).isEqualTo("APPROVED");
        assertThat(result.previousContent()).isEqualTo(result.reviewedContent());
        assertThat(result.reviewer().id()).isEqualTo(reviewer.getId());
        verify(userStoryRepository).save(story);
        verify(reviewRepository).save(any(HumanReview.class));
    }

    @Test
    void modifiesPendingCriteriaAndStoresBeforeAndAfterContent() {
        stubAnalystReviewer();
        stubReviewPersistence();
        Requirement requirement = requirement();
        AcceptanceCriteria criteria = criteria(requirement, CriteriaStatus.PENDING_REVIEW);
        when(criteriaRepository.findById(criteria.getId())).thenReturn(Optional.of(criteria));
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);
        when(criteriaRepository.save(criteria)).thenReturn(criteria);
        var edited = objectMapper.createObjectNode()
                .put("title", "Checkout completes")
                .put("description", "Given valid payment, when submitted, then checkout completes.")
                .put("criteriaType", "BEHAVIORAL");

        var result = service.review(ReviewArtifactType.ACCEPTANCE_CRITERIA, criteria.getId(),
                new HumanReviewRequest(ReviewAction.MODIFY, null, edited), principal);

        assertThat(criteria.getTitle()).isEqualTo("Checkout completes");
        assertThat(criteria.getStatus()).isEqualTo(CriteriaStatus.APPROVED);
        assertThat(result.action()).isEqualTo(ReviewAction.MODIFY);
        assertThat(result.previousContent().path("title").asText()).isEqualTo("Guest checkout");
        assertThat(result.reviewedContent().path("title").asText()).isEqualTo("Checkout completes");
        verify(criteriaRepository).save(criteria);
    }

    @Test
    void rejectsPendingSrsWithReasonAndPreservesContent() {
        stubAnalystReviewer();
        stubReviewPersistence();
        Project project = project();
        SrsDocument document = new SrsDocument(project, "Checkout SRS", objectMapper.createObjectNode()
                .put("title", "Checkout SRS").put("overview", "Checkout overview."));
        document.setId(UUID.randomUUID());
        document.setStatus(SrsDocumentStatus.PENDING_REVIEW);
        when(srsDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(srsDocumentRepository.save(document)).thenReturn(document);

        var result = service.review(ReviewArtifactType.SRS_DOCUMENT, document.getId(),
                new HumanReviewRequest(ReviewAction.REJECT, "Missing payment failure behavior.", null), principal);

        assertThat(document.getStatus()).isEqualTo(SrsDocumentStatus.REJECTED);
        assertThat(document.getTitle()).isEqualTo("Checkout SRS");
        assertThat(result.reason()).isEqualTo("Missing payment failure behavior.");
        assertThat(result.previousContent()).isEqualTo(result.reviewedContent());
        verify(srsDocumentRepository).save(document);
    }

    @Test
    void rejectsTransitionForAlreadyApprovedArtifact() {
        stubAnalystReviewer();
        Requirement requirement = requirement();
        UserStory story = story(requirement, StoryStatus.APPROVED);
        when(userStoryRepository.findById(story.getId())).thenReturn(Optional.of(story));
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);

        assertThatThrownBy(() -> service.review(ReviewArtifactType.USER_STORY, story.getId(),
                new HumanReviewRequest(ReviewAction.ACCEPT, null, null), principal))
                .isInstanceOf(ConflictException.class);
        verify(userStoryRepository, never()).save(any(UserStory.class));
    }

    @Test
    void rejectsTransitionForAlreadyRejectedArtifact() {
        stubAnalystReviewer();
        Requirement requirement = requirement();
        AcceptanceCriteria criteria = criteria(requirement, CriteriaStatus.REJECTED);
        when(criteriaRepository.findById(criteria.getId())).thenReturn(Optional.of(criteria));
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);

        assertThatThrownBy(() -> service.review(ReviewArtifactType.ACCEPTANCE_CRITERIA, criteria.getId(),
                new HumanReviewRequest(ReviewAction.ACCEPT, null, null), principal))
                .isInstanceOf(ConflictException.class);
        verify(criteriaRepository, never()).save(any(AcceptanceCriteria.class));
    }

    @Test
        void rejectionRequiresAReasonBeforeArtifactLookup() {
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_BUSINESS_ANALYST")))
            .when(principal).getAuthorities();
        assertThatThrownBy(() -> service.review(ReviewArtifactType.USER_STORY, UUID.randomUUID(),
                new HumanReviewRequest(ReviewAction.REJECT, " ", null), principal))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(userStoryRepository, criteriaRepository, srsDocumentRepository,
            projectService, reviewRepository);
        }

        @Test
        void modifyRequiresSchemaValidEditedContent() {
        stubAnalystReviewer();
        Requirement requirement = requirement();
        UserStory story = story(requirement, StoryStatus.PENDING_REVIEW);
        when(userStoryRepository.findById(story.getId())).thenReturn(Optional.of(story));
        when(requirementService.findAccessibleRequirement(requirement.getId(), principal)).thenReturn(requirement);

        assertThatThrownBy(() -> service.review(ReviewArtifactType.USER_STORY, story.getId(),
            new HumanReviewRequest(ReviewAction.MODIFY, null, objectMapper.createObjectNode()), principal))
                .isInstanceOf(BadRequestException.class);
        verify(userStoryRepository, never()).save(any(UserStory.class));
        verify(reviewRepository, never()).save(any(HumanReview.class));
    }

    @Test
    void deniesUnauthorizedReviewerBeforeLoadingArtifact() {
        org.mockito.Mockito.doReturn(List.of(new SimpleGrantedAuthority("ROLE_CLIENT")))
            .when(principal).getAuthorities();

        assertThatThrownBy(() -> service.review(ReviewArtifactType.USER_STORY, UUID.randomUUID(),
                new HumanReviewRequest(ReviewAction.ACCEPT, null, null), principal))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(userStoryRepository, criteriaRepository, srsDocumentRepository);
    }

    private Requirement requirement() {
        Project project = project();
        Requirement requirement = new Requirement(project, "Guest checkout", "Details.",
                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM,
                RequirementStatus.ANALYSIS_COMPLETED, "client", project.getClient(), null);
        requirement.setId(UUID.randomUUID());
        return requirement;
    }

    private Project project() {
        User owner = new User("Project", "Owner", "owner@example.com", "hash", null);
        owner.setId(UUID.randomUUID());
        Project project = new Project("Checkout", "Description", ProjectStatus.ACTIVE, null, null, owner);
        project.setId(UUID.randomUUID());
        return project;
    }

    private UserStory story(Requirement requirement, StoryStatus status) {
        UserStory story = new UserStory(requirement, "Guest checkout", "Shopper checks out",
                "As a shopper, I want guest checkout.", RequirementPriority.MEDIUM,
                status, requirement.getCreatedBy());
        story.setId(UUID.randomUUID());
        return story;
    }

    private AcceptanceCriteria criteria(Requirement requirement, CriteriaStatus status) {
        AcceptanceCriteria criteria = new AcceptanceCriteria(requirement, null, "Guest checkout",
                "Given a shopper, when checking out, then purchase completes.", CriteriaType.BEHAVIORAL, status);
        criteria.setId(UUID.randomUUID());
        return criteria;
    }
}