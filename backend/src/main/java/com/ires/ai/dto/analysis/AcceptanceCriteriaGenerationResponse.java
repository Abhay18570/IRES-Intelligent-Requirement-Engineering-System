package com.ires.ai.dto.analysis;

import com.ires.requirement.criteria.entity.CriteriaType;

import java.util.List;

public record AcceptanceCriteriaGenerationResponse(List<GeneratedCriterion> criteria) {

    public record GeneratedCriterion(
            String title,
            String description,
            CriteriaType criteriaType
    ) {
    }
}