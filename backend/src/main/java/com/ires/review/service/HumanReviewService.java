package com.ires.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ires.common.exception.BadRequestException;
import com.ires.common.exception.ConflictException;
import com.ires.common.exception.ForbiddenException;
import com.ires.common.exception.NotFoundException;
import com.ires.project.service.ProjectService;
import com.ires.requirement.criteria.entity.AcceptanceCriteria;
import com.ires.requirement.criteria.entity.CriteriaStatus;
import com.ires.requirement.criteria.entity.CriteriaType;
import com.ires.requirement.criteria.repository.AcceptanceCriteriaRepository;
import com.ires.requirement.service.RequirementService;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.review.dto.HumanReviewRequest;
import com.ires.review.dto.HumanReviewResponse;
import com.ires.review.entity.HumanReview;
import com.ires.review.entity.ReviewAction;
import com.ires.review.entity.ReviewArtifactType;
import com.ires.review.repository.HumanReviewRepository;
import com.ires.srs.dto.SrsDocumentResponse;
import com.ires.srs.entity.SrsDocument;
import com.ires.srs.entity.SrsDocumentStatus;
import com.ires.srs.repository.SrsDocumentRepository;
import com.ires.story.entity.StoryStatus;
import com.ires.story.entity.UserStory;
import com.ires.story.repository.UserStoryRepository;
import com.ires.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HumanReviewService {

    private final UserStoryRepository userStoryRepository;
    private final AcceptanceCriteriaRepository criteriaRepository;
    private final SrsDocumentRepository srsDocumentRepository;
    private final HumanReviewRepository reviewRepository;
    private final RequirementService requirementService;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    @Transactional
    public HumanReviewResponse review(
            ReviewArtifactType artifactType,
            UUID artifactId,
            HumanReviewRequest request,
            UserDetails principal
    ) {
        assertReviewer(principal);
        validateRequest(request);
        User reviewer = projectService.currentUser(principal);
        return switch (artifactType) {
            case USER_STORY -> reviewStory(artifactId, request, reviewer, principal);
            case ACCEPTANCE_CRITERIA -> reviewCriteria(artifactId, request, reviewer, principal);
            case SRS_DOCUMENT -> reviewSrs(artifactId, request, reviewer, principal);
        };
    }

    public List<HumanReviewResponse> history(
            ReviewArtifactType artifactType,
            UUID artifactId,
            UserDetails principal
    ) {
        assertReviewer(principal);
        assertArtifactAccessible(artifactType, artifactId, principal);
        return reviewRepository.findByArtifactTypeAndArtifactIdOrderByReviewedAtDesc(artifactType, artifactId)
                .stream().map(HumanReviewResponse::from).toList();
    }

    private HumanReviewResponse reviewStory(
            UUID id, HumanReviewRequest request, User reviewer, UserDetails principal
    ) {
        UserStory story = userStoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User story not found."));
        requirementService.findAccessibleRequirement(story.getRequirement().getId(), principal);
        requirePending(story.getStatus() == StoryStatus.PENDING_REVIEW);
        JsonNode previous = objectMapper.valueToTree(new StoryContent(
                story.getTitle(), story.getDescription(), story.getStoryText(), story.getPriority().name()));

        if (request.action() == ReviewAction.MODIFY) {
            JsonNode edited = requireEditedObject(request.editedContent());
            story.setTitle(requiredText(edited, "title", 300));
            story.setDescription(optionalText(edited, "description", 10000));
            story.setStoryText(requiredText(edited, "storyText", 10000));
            story.setPriority(enumValue(RequirementPriority.class, requiredText(edited, "priority", 10), "priority"));
        }
        story.setStatus(request.action() == ReviewAction.REJECT ? StoryStatus.REJECTED : StoryStatus.APPROVED);
        UserStory saved = userStoryRepository.save(story);
        JsonNode reviewed = objectMapper.valueToTree(new StoryContent(
                saved.getTitle(), saved.getDescription(), saved.getStoryText(), saved.getPriority().name()));
        return saveReview(ReviewArtifactType.USER_STORY, id, request, reviewer, saved.getStatus().name(), previous, reviewed);
    }

    private HumanReviewResponse reviewCriteria(
            UUID id, HumanReviewRequest request, User reviewer, UserDetails principal
    ) {
        AcceptanceCriteria criteria = criteriaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Acceptance criteria not found."));
        requirementService.findAccessibleRequirement(criteria.getRequirement().getId(), principal);
        requirePending(criteria.getStatus() == CriteriaStatus.PENDING_REVIEW);
        JsonNode previous = objectMapper.valueToTree(new CriteriaContent(
                criteria.getTitle(), criteria.getDescription(), criteria.getCriteriaType().name()));

        if (request.action() == ReviewAction.MODIFY) {
            JsonNode edited = requireEditedObject(request.editedContent());
            criteria.setTitle(requiredText(edited, "title", 300));
            criteria.setDescription(optionalText(edited, "description", 10000));
            criteria.setCriteriaType(enumValue(CriteriaType.class,
                    requiredText(edited, "criteriaType", 20), "criteriaType"));
        }
        criteria.setStatus(request.action() == ReviewAction.REJECT ? CriteriaStatus.REJECTED : CriteriaStatus.APPROVED);
        AcceptanceCriteria saved = criteriaRepository.save(criteria);
        JsonNode reviewed = objectMapper.valueToTree(new CriteriaContent(
                saved.getTitle(), saved.getDescription(), saved.getCriteriaType().name()));
        return saveReview(ReviewArtifactType.ACCEPTANCE_CRITERIA, id, request, reviewer,
                saved.getStatus().name(), previous, reviewed);
    }

    private HumanReviewResponse reviewSrs(
            UUID id, HumanReviewRequest request, User reviewer, UserDetails principal
    ) {
        SrsDocument document = srsDocumentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("SRS document not found."));
        projectService.assertCanView(document.getProject(), principal);
        requirePending(document.getStatus() == SrsDocumentStatus.PENDING_REVIEW);
        JsonNode previous = objectMapper.valueToTree(new SrsContent(document.getTitle(), document.getContent()));

        if (request.action() == ReviewAction.MODIFY) {
            JsonNode edited = requireEditedObject(request.editedContent());
            validateSrsContent(edited);
            document.setTitle(edited.get("title").asText().trim());
            document.setContent(edited.deepCopy());
        }
        document.setStatus(request.action() == ReviewAction.REJECT
                ? SrsDocumentStatus.REJECTED : SrsDocumentStatus.APPROVED);
        SrsDocument saved = srsDocumentRepository.save(document);
        JsonNode reviewed = objectMapper.valueToTree(new SrsContent(saved.getTitle(), saved.getContent()));
        return saveReview(ReviewArtifactType.SRS_DOCUMENT, id, request, reviewer,
                saved.getStatus().name(), previous, reviewed);
    }

    private HumanReviewResponse saveReview(
            ReviewArtifactType type,
            UUID id,
            HumanReviewRequest request,
            User reviewer,
            String status,
            JsonNode previous,
            JsonNode reviewed
    ) {
        HumanReview review = new HumanReview(
                type, id, request.action(), status, reviewer, request.reason(), previous, reviewed);
        return HumanReviewResponse.from(reviewRepository.save(review));
    }

    private void validateRequest(HumanReviewRequest request) {
        if (request == null || request.action() == null) {
            throw new BadRequestException("A review action is required.");
        }
        if (request.reason() != null && request.reason().length() > 1000) {
            throw new BadRequestException("Review reason cannot exceed 1000 characters.");
        }
        if (request.action() == ReviewAction.REJECT && !hasText(request.reason())) {
            throw new BadRequestException("A rejection reason is required.");
        }
        if (request.action() == ReviewAction.MODIFY && request.editedContent() == null) {
            throw new BadRequestException("Edited content is required for a modify action.");
        }
        if (request.action() != ReviewAction.MODIFY && request.editedContent() != null) {
            throw new BadRequestException("Edited content is only valid for a modify action.");
        }
    }

    private JsonNode requireEditedObject(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw new BadRequestException("Edited content must be a JSON object.");
        }
        return content;
    }

    private String requiredText(JsonNode content, String field, int maxLength) {
        if (!content.has(field) || !content.get(field).isTextual() || !hasText(content.get(field).asText())) {
            throw new BadRequestException("Edited content requires a non-empty " + field + ".");
        }
        String value = content.get(field).asText().trim();
        if (value.length() > maxLength) {
            throw new BadRequestException(field + " cannot exceed " + maxLength + " characters.");
        }
        return value;
    }

    private String optionalText(JsonNode content, String field, int maxLength) {
        if (!content.has(field) || content.get(field).isNull()) {
            return null;
        }
        if (!content.get(field).isTextual()) {
            throw new BadRequestException(field + " must be text.");
        }
        String value = content.get(field).asText();
        if (value.length() > maxLength) {
            throw new BadRequestException(field + " cannot exceed " + maxLength + " characters.");
        }
        return value;
    }

    private <E extends Enum<E>> E enumValue(Class<E> enumType, String value, String field) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Edited content contains an invalid " + field + ".");
        }
    }

    private void validateSrsContent(JsonNode content) {
        requiredText(content, "title", 300);
        requiredText(content, "overview", 10000);
        List<String> requirements = List.of(
                "functionalRequirements", "nonFunctionalRequirements",
                "businessRequirements", "technicalRequirements");
        List<String> optionalSections = List.of("assumptions", "constraints");
        boolean hasRequirement = false;
        for (String field : requirements) {
            List<String> values = section(content, field);
            hasRequirement |= !values.isEmpty();
        }
        for (String field : optionalSections) {
            section(content, field);
        }
        if (!hasRequirement) {
            throw new BadRequestException("Edited SRS must contain at least one requirement.");
        }
    }

    private List<String> section(JsonNode content, String field) {
        JsonNode section = content.get(field);
        if (section == null || !section.isArray()) {
            throw new BadRequestException("Edited SRS requires an array for " + field + ".");
        }
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode value : section) {
            if (!value.isTextual() || !hasText(value.asText()) || value.asText().length() > 10000) {
                throw new BadRequestException("Edited SRS contains an invalid entry in " + field + ".");
            }
            values.add(value.asText().trim());
        }
        return values;
    }

    private void assertArtifactAccessible(ReviewArtifactType type, UUID id, UserDetails principal) {
        switch (type) {
            case USER_STORY -> {
                UserStory story = userStoryRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException("User story not found."));
                requirementService.findAccessibleRequirement(story.getRequirement().getId(), principal);
            }
            case ACCEPTANCE_CRITERIA -> {
                AcceptanceCriteria criteria = criteriaRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException("Acceptance criteria not found."));
                requirementService.findAccessibleRequirement(criteria.getRequirement().getId(), principal);
            }
            case SRS_DOCUMENT -> {
                SrsDocument document = srsDocumentRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException("SRS document not found."));
                projectService.assertCanView(document.getProject(), principal);
            }
        }
    }

    private void assertReviewer(UserDetails principal) {
        boolean reviewer = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> "ROLE_ADMIN".equals(role) || "ROLE_BUSINESS_ANALYST".equals(role));
        if (!reviewer) {
            throw new ForbiddenException("Only business analysts or admins can review generated artifacts.");
        }
    }

    private void requirePending(boolean pending) {
        if (!pending) {
            throw new ConflictException("Only artifacts pending review can be reviewed.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record StoryContent(String title, String description, String storyText, String priority) {
    }

    private record CriteriaContent(String title, String description, String criteriaType) {
    }

    private record SrsContent(String title, JsonNode content) {
    }
}