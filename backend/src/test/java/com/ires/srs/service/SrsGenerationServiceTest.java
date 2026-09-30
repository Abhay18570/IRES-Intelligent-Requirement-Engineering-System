package com.ires.srs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.ai.dto.srs.SrsGenerationRequest;
import com.ires.ai.dto.srs.SrsGenerationResponse;
import com.ires.ai.service.AIAnalysisProvider;
import com.ires.common.exception.BadRequestException;
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
import com.ires.requirement.repository.RequirementRepository;
import com.ires.story.entity.StoryStatus;
import com.ires.story.entity.UserStory;
import com.ires.story.repository.UserStoryRepository;
import com.ires.srs.entity.SrsDocument;
import com.ires.srs.entity.SrsDocumentStatus;
import com.ires.srs.repository.SrsDocumentRepository;
import com.ires.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SrsGenerationServiceTest {

    @Mock
    private ProjectService projectService;

    @Mock
    private RequirementRepository requirementRepository;

    @Mock
    private UserStoryRepository userStoryRepository;

    @Mock
    private AcceptanceCriteriaRepository criteriaRepository;

    @Mock
    private SrsDocumentRepository srsDocumentRepository;

    @Mock
    private AIAnalysisProvider analysisProvider;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserDetails principal;

    @InjectMocks
    private SrsGenerationService service;

    @Test
    void gathersProjectArtifactsAndPersistsNewDraftSnapshot() {
        Project project = project();
        Requirement requirement = requirement(project);
        UserStory story = new UserStory(requirement, "Guest checkout", "Shopper checks out",
                "As a shopper, I want guest checkout so I can purchase without registering.",
                RequirementPriority.MEDIUM, StoryStatus.DRAFT, project.getClient());
        story.setId(UUID.randomUUID());
        AcceptanceCriteria criterion = new AcceptanceCriteria(requirement, story, "Purchase completes",
                "Given valid details, when submitted, then the purchase completes.",
                CriteriaType.BEHAVIORAL, CriteriaStatus.DRAFT);
        criterion.setId(UUID.randomUUID());

        when(projectService.findProject(project.getId())).thenReturn(project);
        when(requirementRepository.findByProjectId(project.getId())).thenReturn(List.of(requirement));
        when(userStoryRepository.findByRequirementIdIn(List.of(requirement.getId()))).thenReturn(List.of(story));
        when(criteriaRepository.findByRequirementIdIn(List.of(requirement.getId()))).thenReturn(List.of(criterion));
        when(analysisProvider.generateSrs(any())).thenReturn(new SrsGenerationResponse(
                "Checkout SRS",
                "A web checkout service.",
                List.of("The system shall support guest checkout."),
                List.of(),
                List.of(),
                List.of(),
                List.of("The payment provider is configured."),
                List.of()
        ));
        UUID documentId = UUID.randomUUID();
        when(srsDocumentRepository.save(any(SrsDocument.class))).thenAnswer(invocation -> {
            SrsDocument document = invocation.getArgument(0);
            document.setId(documentId);
            return document;
        });

        var response = service.generate(project.getId(), principal);

        assertThat(response.id()).isEqualTo(documentId);
        assertThat(response.projectId()).isEqualTo(project.getId());
        assertThat(response.status()).isEqualTo(SrsDocumentStatus.DRAFT);
        assertThat(response.content().path("functionalRequirements").get(0).asText())
                .isEqualTo("The system shall support guest checkout.");

        ArgumentCaptor<SrsGenerationRequest> requestCaptor = ArgumentCaptor.forClass(SrsGenerationRequest.class);
        verify(analysisProvider).generateSrs(requestCaptor.capture());
        SrsGenerationRequest request = requestCaptor.getValue();
        assertThat(request.projectName()).isEqualTo("Checkout");
        assertThat(request.requirements()).hasSize(1);
        assertThat(request.requirements().get(0).userStories()).hasSize(1);
        assertThat(request.requirements().get(0).acceptanceCriteria()).hasSize(1);
        verify(projectService).assertCanView(project, principal);
        verify(srsDocumentRepository).save(any(SrsDocument.class));
    }

    @Test
    void rejectsProjectWithoutRequirementsBeforeCallingProvider() {
        Project project = project();
        when(projectService.findProject(project.getId())).thenReturn(project);
        when(requirementRepository.findByProjectId(project.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.generate(project.getId(), principal))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(analysisProvider, srsDocumentRepository);
    }

    @Test
    void rejectsEmptySrsResponseWithoutPersisting() {
        Project project = project();
        Requirement requirement = requirement(project);
        when(projectService.findProject(project.getId())).thenReturn(project);
        when(requirementRepository.findByProjectId(project.getId())).thenReturn(List.of(requirement));
        when(userStoryRepository.findByRequirementIdIn(List.of(requirement.getId()))).thenReturn(List.of());
        when(criteriaRepository.findByRequirementIdIn(List.of(requirement.getId()))).thenReturn(List.of());
        when(analysisProvider.generateSrs(any())).thenReturn(new SrsGenerationResponse(
                "Empty SRS", "Overview", List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));

        assertThatThrownBy(() -> service.generate(project.getId(), principal))
                .isInstanceOf(com.ires.common.exception.ServiceUnavailableException.class);
        verify(srsDocumentRepository, never()).save(any(SrsDocument.class));
    }

    private Project project() {
        User owner = new User("Test", "Owner", "owner@example.com", "hash", null);
        owner.setId(UUID.randomUUID());
        Project project = new Project("Checkout", "Checkout project description.", ProjectStatus.ACTIVE,
                null, null, owner);
        project.setId(UUID.randomUUID());
        return project;
    }

    private Requirement requirement(Project project) {
        Requirement requirement = new Requirement(project, "Guest checkout", "Allow guest purchases.",
                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM,
                RequirementStatus.ANALYSIS_COMPLETED, "client", project.getClient(), null);
        requirement.setId(UUID.randomUUID());
        return requirement;
    }
}