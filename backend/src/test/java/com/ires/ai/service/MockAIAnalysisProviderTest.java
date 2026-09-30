package com.ires.ai.service;

import com.ires.ai.dto.analysis.RequirementImprovementResponse;
import com.ires.ai.dto.analysis.AcceptanceCriteriaGenerationResponse;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementStatus;
import com.ires.requirement.entity.RequirementType;
import com.ires.requirement.criteria.entity.CriteriaType;
import com.ires.story.dto.GeneratedUserStory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockAIAnalysisProviderTest {

    @Test
    void returnsDeterministicRequirementImprovementProposal() {
        UUID requirementId = UUID.randomUUID();
        Requirement requirement = new Requirement(null, "Original title", "Original description",
                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM, RequirementStatus.DRAFT,
                "TEST", null, null);
        requirement.setId(requirementId);
        MockAIAnalysisProvider provider = new MockAIAnalysisProvider(true);

        RequirementImprovementResponse response = provider.improveRequirement(requirement);

        assertThat(response.requirementId()).isEqualTo(requirementId);
        assertThat(response.originalTitle()).isEqualTo("Original title");
        assertThat(response.proposedTitle()).isEqualTo("Original title");
        assertThat(response.originalDescription()).isEqualTo("Original description");
        assertThat(response.proposedDescription()).isEqualTo("Original description");
        assertThat(response.rationale()).isNotBlank();
        assertThat(response.confidence()).isBetween(new java.math.BigDecimal("0"), new java.math.BigDecimal("1"));
    }

    @Test
    void reportsUnavailableProviderWhenMockIsDisabled() {
        MockAIAnalysisProvider provider = new MockAIAnalysisProvider(false);
        Requirement requirement = new Requirement(null, "Original title", "Original description",
                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM, RequirementStatus.DRAFT,
                "TEST", null, null);

        assertThatThrownBy(() -> provider.improveRequirement(requirement))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI analysis provider is unavailable.");
    }

    @Test
    void generatesUserStoryCompatibleWithExistingStoryFields() {
        Requirement requirement = new Requirement(null, "Guest checkout", "Allow guest purchases.",
                RequirementType.FUNCTIONAL, RequirementPriority.HIGH, RequirementStatus.DRAFT,
                "TEST", null, null);
        MockAIAnalysisProvider provider = new MockAIAnalysisProvider(true);

        GeneratedUserStory story = provider.generateUserStory(requirement);

        assertThat(story.title()).isEqualTo("Guest checkout");
        assertThat(story.description()).isEqualTo("Allow guest purchases.");
        assertThat(story.storyText()).contains("As a user, I want Guest checkout");
        assertThat(story.priority()).isEqualTo(RequirementPriority.HIGH);
    }

    @Test
    void reportsUnavailableProviderWhenMockStoryGenerationIsDisabled() {
        MockAIAnalysisProvider provider = new MockAIAnalysisProvider(false);
        Requirement requirement = new Requirement(null, "Guest checkout", "Allow guest purchases.",
                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM, RequirementStatus.DRAFT,
                "TEST", null, null);

        assertThatThrownBy(() -> provider.generateUserStory(requirement))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("AI analysis provider is unavailable.");
    }

        @Test
        void generatesDeterministicAcceptanceCriteriaFromRequirement() {
                Requirement requirement = new Requirement(null, "Guest checkout", "Allow guest purchases.",
                                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM, RequirementStatus.DRAFT,
                                "TEST", null, null);
                MockAIAnalysisProvider provider = new MockAIAnalysisProvider(true);

                AcceptanceCriteriaGenerationResponse response = provider.generateAcceptanceCriteria(requirement, null);

                assertThat(response.criteria()).hasSize(2);
                assertThat(response.criteria().get(0).title()).isEqualTo("Expected behavior is completed");
                assertThat(response.criteria().get(0).description()).contains("Given Guest checkout is available");
                assertThat(response.criteria().get(0).criteriaType()).isEqualTo(CriteriaType.BEHAVIORAL);
        }

        @Test
        void reportsUnavailableProviderWhenMockCriteriaGenerationIsDisabled() {
                MockAIAnalysisProvider provider = new MockAIAnalysisProvider(false);
                Requirement requirement = new Requirement(null, "Guest checkout", "Allow guest purchases.",
                                RequirementType.FUNCTIONAL, RequirementPriority.MEDIUM, RequirementStatus.DRAFT,
                                "TEST", null, null);

                assertThatThrownBy(() -> provider.generateAcceptanceCriteria(requirement, null))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessage("AI analysis provider is unavailable.");
        }
}