package com.ires.ai.dto.srs;

import com.ires.requirement.criteria.entity.CriteriaType;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementStatus;
import com.ires.requirement.entity.RequirementType;

import java.util.List;
import java.util.UUID;

public record SrsGenerationRequest(
        String projectName,
        String projectDescription,
        List<RequirementContext> requirements
) {
    public record RequirementContext(
            UUID requirementId,
            String title,
            String description,
            RequirementType requirementType,
            RequirementPriority priority,
            RequirementStatus status,
            List<UserStoryContext> userStories,
            List<AcceptanceCriteriaContext> acceptanceCriteria
    ) {
    }

    public record UserStoryContext(
            String title,
            String description,
            String storyText
    ) {
    }

    public record AcceptanceCriteriaContext(
            String title,
            String description,
            CriteriaType criteriaType
    ) {
    }
}