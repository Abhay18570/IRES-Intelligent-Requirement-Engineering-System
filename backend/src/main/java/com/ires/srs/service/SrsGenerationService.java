package com.ires.srs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.ai.dto.srs.SrsGenerationRequest;
import com.ires.ai.dto.srs.SrsGenerationResponse;
import com.ires.ai.service.AIAnalysisProvider;
import com.ires.common.exception.BadRequestException;
import com.ires.common.exception.ServiceUnavailableException;
import com.ires.project.entity.Project;
import com.ires.project.service.ProjectService;
import com.ires.requirement.criteria.entity.AcceptanceCriteria;
import com.ires.requirement.criteria.repository.AcceptanceCriteriaRepository;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.repository.RequirementRepository;
import com.ires.story.entity.UserStory;
import com.ires.story.repository.UserStoryRepository;
import com.ires.srs.dto.SrsDocumentResponse;
import com.ires.srs.entity.SrsDocument;
import com.ires.srs.repository.SrsDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SrsGenerationService {

    private final ProjectService projectService;
    private final RequirementRepository requirementRepository;
    private final UserStoryRepository userStoryRepository;
    private final AcceptanceCriteriaRepository criteriaRepository;
    private final SrsDocumentRepository srsDocumentRepository;
    private final AIAnalysisProvider analysisProvider;
    private final ObjectMapper objectMapper;

    @Transactional
    public SrsDocumentResponse generate(UUID projectId, UserDetails principal) {
        Project project = projectService.findProject(projectId);
        projectService.assertCanView(project, principal);

        List<Requirement> requirements = requirementRepository.findByProjectId(projectId);
        if (requirements.isEmpty()) {
            throw new BadRequestException("Add at least one requirement before generating an SRS.");
        }

        List<UUID> requirementIds = requirements.stream().map(Requirement::getId).toList();
        List<UserStory> stories = userStoryRepository.findByRequirementIdIn(requirementIds);
        List<AcceptanceCriteria> criteria = criteriaRepository.findByRequirementIdIn(requirementIds);
        Map<UUID, List<UserStory>> storiesByRequirement = stories.stream().collect(
                Collectors.groupingBy(story -> story.getRequirement().getId()));
        Map<UUID, List<AcceptanceCriteria>> criteriaByRequirement = criteria.stream().collect(
                Collectors.groupingBy(criterion -> criterion.getRequirement().getId()));

        SrsGenerationRequest request = new SrsGenerationRequest(
                project.getName(),
                project.getDescription(),
                requirements.stream().map(requirement -> new SrsGenerationRequest.RequirementContext(
                        requirement.getId(),
                        requirement.getTitle(),
                        requirement.getDescription(),
                        requirement.getRequirementType(),
                        requirement.getPriority(),
                        requirement.getStatus(),
                        storiesByRequirement.getOrDefault(requirement.getId(), List.of()).stream()
                                .map(story -> new SrsGenerationRequest.UserStoryContext(
                                        story.getTitle(), story.getDescription(), story.getStoryText()))
                                .toList(),
                        criteriaByRequirement.getOrDefault(requirement.getId(), List.of()).stream()
                                .map(criterion -> new SrsGenerationRequest.AcceptanceCriteriaContext(
                                        criterion.getTitle(), criterion.getDescription(), criterion.getCriteriaType()))
                                .toList()
                )).toList()
        );

        SrsGenerationResponse generated;
        try {
            generated = analysisProvider.generateSrs(request);
            validate(generated);
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("The SRS could not be generated.");
        }

        SrsDocument document = new SrsDocument(
                project,
                generated.title().trim(),
                objectMapper.valueToTree(generated)
        );
        return SrsDocumentResponse.from(srsDocumentRepository.save(document));
    }

    private void validate(SrsGenerationResponse generated) {
        if (generated == null || !hasText(generated.title()) || generated.title().trim().length() > 300
                || !hasText(generated.overview()) || generated.overview().trim().length() > 10000) {
            throw new IllegalStateException("SRS response is missing required content.");
        }
        validateSection(generated.functionalRequirements());
        validateSection(generated.nonFunctionalRequirements());
        validateSection(generated.businessRequirements());
        validateSection(generated.technicalRequirements());
        validateSection(generated.assumptions());
        validateSection(generated.constraints());
        if (generated.functionalRequirements().isEmpty()
                && generated.nonFunctionalRequirements().isEmpty()
                && generated.businessRequirements().isEmpty()
                && generated.technicalRequirements().isEmpty()) {
            throw new IllegalStateException("SRS response contains no requirements.");
        }
    }

    private void validateSection(List<String> section) {
        if (section == null || section.stream().anyMatch(value -> !hasText(value) || value.trim().length() > 10000)) {
            throw new IllegalStateException("SRS response contains an invalid section.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}