package com.ires.ai.provider.nvidia;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.ai.dto.analysis.AmbiguityFinding;
import com.ires.ai.dto.analysis.AmbiguityRequest;
import com.ires.ai.dto.analysis.AmbiguityResponse;
import com.ires.ai.dto.analysis.ClassificationRequest;
import com.ires.ai.dto.analysis.ClassificationResponse;
import com.ires.ai.dto.analysis.CompletenessRequest;
import com.ires.ai.dto.analysis.CompletenessResponse;
import com.ires.ai.dto.analysis.MissingInformation;
import com.ires.ai.dto.analysis.ConflictDetectionRequest;
import com.ires.ai.dto.analysis.ConflictDetectionResponse;
import com.ires.ai.dto.analysis.ConflictFinding;
import com.ires.ai.dto.analysis.DuplicateDetectionRequest;
import com.ires.ai.dto.analysis.DuplicateDetectionResponse;
import com.ires.ai.dto.analysis.DuplicateCandidate;
import com.ires.ai.dto.analysis.RequirementCandidate;
import com.ires.ai.dto.analysis.QualityAnalysisRequest;
import com.ires.ai.dto.analysis.QualityAnalysisResponse;
import com.ires.ai.dto.analysis.QualityDimension;
import com.ires.ai.provider.nvidia.dto.NvidiaChatMessage;
import com.ires.ai.provider.nvidia.dto.NvidiaChatRequest;
import com.ires.ai.provider.nvidia.dto.NvidiaChatResponse;
import com.ires.ai.service.AIAnalysisProvider;
import com.ires.requirement.entity.Requirement;


import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

public class NvidiaAIAnalysisProvider implements AIAnalysisProvider {

    private static final Set<String> VALID_CLASSIFICATIONS = Set.of(
            "FUNCTIONAL", "NON_FUNCTIONAL", "BUSINESS", "TECHNICAL", "UNCLEAR"
    );

    private final NvidiaApiClient client;
    private final String model;
    private final ObjectMapper objectMapper;

    public NvidiaAIAnalysisProvider(
            NvidiaApiClient client,
            String model
    ) {
        this.client = client;
        this.model = model;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ClassificationResponse classify(ClassificationRequest request) {
        String systemPrompt = """
                You are a software requirements classification engine.
                Classify the given requirement into exactly one category.
                Valid categories: FUNCTIONAL, NON_FUNCTIONAL, BUSINESS, TECHNICAL, UNCLEAR.
                Respond ONLY with a JSON object in this exact format:
                {"classification": "<CATEGORY>", "confidence": <0.0-1.0>, "reason": "<explanation>"}
                Do not include any other text outside the JSON object.""";

        String userPrompt = "Classify this software requirement:\n\n" + request.text();

        NvidiaChatRequest chatRequest = new NvidiaChatRequest(
                resolveModel(),
                List.of(
                        new NvidiaChatMessage("system", systemPrompt),
                        new NvidiaChatMessage("user", userPrompt)
                )
        );

        NvidiaChatResponse chatResponse = client.chatCompletion(chatRequest);

        String content = extractContent(chatResponse);
        JsonNode json = parseJson(content);

        String classification = extractRequiredString(json, "classification");

        if (!VALID_CLASSIFICATIONS.contains(classification)) {
            throw new IllegalStateException(
                    "NVIDIA returned an invalid classification: " + classification
            );
        }

        BigDecimal confidence = extractConfidence(json);

        String reason = json.has("reason")
                ? json.get("reason").asText("")
                : "";

        if (reason.isBlank()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a reason."
            );
        }

        return new ClassificationResponse(
                classification,
                confidence,
                reason
        );
    }

    /*
    * NVIDIA analysis capabilities implemented as separate vertical slices.
    */
    @Override
    public AmbiguityResponse detectAmbiguity(AmbiguityRequest request) {
        String systemPrompt = """
                You are a software requirements ambiguity detection engine.

                Analyze the supplied software requirement and determine whether
                it contains ambiguous, vague, subjective, imprecise, or
                non-measurable language.

                A requirement is ambiguous when its meaning or expected behavior
                cannot be determined precisely enough for implementation or
                objective testing.

                Examples of potentially ambiguous terms include:
                - fast
                - quickly
                - user-friendly
                - easy to use
                - sufficient
                - appropriate
                - normally
                - soon
                - efficient
                - reliable

                If the requirement is clear and measurable, hasAmbiguity must be false
                and findings must be an empty array.

                If ambiguity exists, identify each important ambiguous phrase.

                Respond ONLY with a JSON object in exactly this structure:
                {
                "hasAmbiguity": true,
                "findings": [
                    {
                    "text": "ambiguous phrase",
                    "reason": "why the phrase is ambiguous",
                    "suggestion": "how to make the requirement precise"
                    }
                ],
                "confidence": 0.0
                }

                confidence must be a number between 0.0 and 1.0.
                Do not include markdown.
                Do not include any text outside the JSON object.
                """;

        String userPrompt =
                "Analyze this software requirement for ambiguity:\n\n"
                        + request.text();

        NvidiaChatRequest chatRequest = new NvidiaChatRequest(
                resolveModel(),
                List.of(
                        new NvidiaChatMessage("system", systemPrompt),
                        new NvidiaChatMessage("user", userPrompt)
                )
        );

        NvidiaChatResponse chatResponse =
                client.chatCompletion(chatRequest);

        String content = extractContent(chatResponse);
        JsonNode json = parseJson(content);

        if (!json.has("hasAmbiguity")
                || json.get("hasAmbiguity").isNull()
                || !json.get("hasAmbiguity").isBoolean()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a valid hasAmbiguity value."
            );
        }

        boolean hasAmbiguity = json.get("hasAmbiguity").booleanValue();

        if (!json.has("findings")
                || json.get("findings").isNull()
                || !json.get("findings").isArray()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a valid findings array."
            );
        }

        List<AmbiguityFinding> findings = new ArrayList<>();

        for (JsonNode findingNode : json.get("findings")) {
            String text = extractFindingField(findingNode, "text");
            String reason = extractFindingField(findingNode, "reason");
            String suggestion = extractFindingField(findingNode, "suggestion");

            findings.add(
                    new AmbiguityFinding(
                            text,
                            reason,
                            suggestion
                    )
            );
        }

        if (!hasAmbiguity && !findings.isEmpty()) {
            throw new IllegalStateException(
                    "NVIDIA response is inconsistent: hasAmbiguity is false "
                            + "but findings were returned."
            );
        }

        if (hasAmbiguity && findings.isEmpty()) {
            throw new IllegalStateException(
                    "NVIDIA response is inconsistent: hasAmbiguity is true "
                            + "but no findings were returned."
            );
        }

        BigDecimal confidence = extractConfidence(json);

        return new AmbiguityResponse(
                hasAmbiguity,
                findings,
                confidence
        );
    }

    @Override
    public CompletenessResponse analyzeCompleteness(
            CompletenessRequest request
    ) {
        String systemPrompt = """
                You are a software requirements completeness analysis engine.

                Analyze the supplied software requirement and determine whether
                it contains enough information for implementation and objective
                verification.

                A requirement may be incomplete when important information is
                missing, such as:
                - required inputs
                - expected outputs
                - business rules
                - processing behavior
                - validation rules
                - error handling
                - boundary conditions
                - security requirements
                - performance requirements
                - constraints
                - dependencies
                - acceptance conditions

                Do not mark a requirement incomplete merely because it does not
                specify information that is irrelevant to its stated behavior.

                If the requirement contains enough information for implementation
                and objective verification:
                - isComplete must be true
                - missingInformation must be an empty array
                - clarificationQuestions must be an empty array

                If important information is missing:
                - isComplete must be false
                - identify each important missing aspect
                - provide a useful clarification question for each important gap

                Respond ONLY with a JSON object in exactly this structure:
                {
                "isComplete": false,
                "missingInformation": [
                    {
                    "aspect": "Error Handling",
                    "description": "The requirement does not specify what should happen when processing fails."
                    }
                ],
                "clarificationQuestions": [
                    "What should the system do when processing fails?"
                ],
                "confidence": 0.0
                }

                confidence must be a number between 0.0 and 1.0.
                Do not include markdown.
                Do not include any text outside the JSON object.
                """;

        String userPrompt =
                "Analyze this software requirement for completeness:\n\n"
                        + request.text();

        NvidiaChatRequest chatRequest = new NvidiaChatRequest(
                resolveModel(),
                List.of(
                        new NvidiaChatMessage("system", systemPrompt),
                        new NvidiaChatMessage("user", userPrompt)
                )
        );

        NvidiaChatResponse chatResponse =
                client.chatCompletion(chatRequest);

        String content = extractContent(chatResponse);
        JsonNode json = parseJson(content);

        if (!json.has("isComplete")
                || json.get("isComplete").isNull()
                || !json.get("isComplete").isBoolean()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a valid isComplete value."
            );
        }

        boolean isComplete = json.get("isComplete").booleanValue();

        if (!json.has("missingInformation")
                || json.get("missingInformation").isNull()
                || !json.get("missingInformation").isArray()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a valid missingInformation array."
            );
        }

        List<MissingInformation> missingInformation = new ArrayList<>();

        for (JsonNode missingNode : json.get("missingInformation")) {
            String aspect = extractCompletenessField(
                    missingNode,
                    "aspect"
            );

            String description = extractCompletenessField(
                    missingNode,
                    "description"
            );

            missingInformation.add(
                    new MissingInformation(
                            aspect,
                            description
                    )
            );
        }

        if (!json.has("clarificationQuestions")
                || json.get("clarificationQuestions").isNull()
                || !json.get("clarificationQuestions").isArray()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a valid clarificationQuestions array."
            );
        }

        List<String> clarificationQuestions = new ArrayList<>();

        for (JsonNode questionNode : json.get("clarificationQuestions")) {
            if (questionNode == null
                    || questionNode.isNull()
                    || !questionNode.isTextual()
                    || questionNode.asText("").isBlank()) {
                throw new IllegalStateException(
                        "NVIDIA completeness response contained an invalid clarification question."
                );
            }

            clarificationQuestions.add(
                    questionNode.asText().trim()
            );
        }

        if (isComplete && !missingInformation.isEmpty()) {
            throw new IllegalStateException(
                    "NVIDIA response is inconsistent: isComplete is true "
                            + "but missing information was returned."
            );
        }

        if (isComplete && !clarificationQuestions.isEmpty()) {
            throw new IllegalStateException(
                    "NVIDIA response is inconsistent: isComplete is true "
                            + "but clarification questions were returned."
            );
        }

        if (!isComplete && missingInformation.isEmpty()) {
            throw new IllegalStateException(
                    "NVIDIA response is inconsistent: isComplete is false "
                            + "but no missing information was returned."
            );
        }

        if (!isComplete && clarificationQuestions.isEmpty()) {
            throw new IllegalStateException(
                    "NVIDIA response is inconsistent: isComplete is false "
                            + "but no clarification questions were returned."
            );
        }

        BigDecimal confidence = extractConfidence(json);

        return new CompletenessResponse(
                isComplete,
                missingInformation,
                clarificationQuestions,
                confidence
        );
    }

    @Override
    public QualityAnalysisResponse analyzeQuality(
            QualityAnalysisRequest request
    ) {
        String systemPrompt = """
                You are a software requirements quality analysis engine.

                Analyze the supplied software requirement against five quality
                dimensions:

                1. Clarity
                The requirement should be understandable and free from vague
                or subjective wording.

                2. Specificity
                The requirement should define precise behavior, constraints,
                inputs, outputs, and measurable conditions where relevant.

                3. Testability
                The requirement should be objectively verifiable through tests,
                inspection, or measurable acceptance conditions.

                4. Consistency
                The requirement should not contain contradictory statements,
                incompatible constraints, or internally inconsistent behavior.

                5. Atomicity
                The requirement should express one coherent requirement rather
                than combining multiple independent requirements unnecessarily.

                Score each dimension from 0 to 100.

                The overallScore must be a 0 to 100 assessment of the requirement
                across all five dimensions.

                For every dimension provide:
                - name
                - score
                - finding explaining the observed quality
                - recommendation for improvement

                Use the exact dimension names:
                Clarity
                Specificity
                Testability
                Consistency
                Atomicity

                Respond ONLY with a JSON object in exactly this structure:
                {
                "overallScore": 82,
                "dimensions": [
                    {
                    "name": "Clarity",
                    "score": 85,
                    "finding": "The requirement is mostly clear.",
                    "recommendation": "Replace vague terms with measurable criteria."
                    },
                    {
                    "name": "Specificity",
                    "score": 80,
                    "finding": "Some behavior is underspecified.",
                    "recommendation": "Define the expected behavior explicitly."
                    },
                    {
                    "name": "Testability",
                    "score": 78,
                    "finding": "The requirement lacks measurable acceptance conditions.",
                    "recommendation": "Add objective conditions that can be verified through testing."
                    },
                    {
                    "name": "Consistency",
                    "score": 90,
                    "finding": "The requirement does not contain an internal contradiction.",
                    "recommendation": "Maintain the current consistent structure."
                    },
                    {
                    "name": "Atomicity",
                    "score": 80,
                    "finding": "The requirement contains one main behavior.",
                    "recommendation": "Separate unrelated behaviors if additional behavior is introduced."
                    }
                ],
                "confidence": 0.91
                }

                overallScore and every dimension score must be integers between
                0 and 100.

                confidence must be a number between 0.0 and 1.0.

                Do not include markdown.
                Do not include any text outside the JSON object.
                """;

        String userPrompt =
                "Analyze this software requirement for quality:\n\n"
                        + request.text();

        NvidiaChatRequest chatRequest = new NvidiaChatRequest(
                resolveModel(),
                List.of(
                        new NvidiaChatMessage("system", systemPrompt),
                        new NvidiaChatMessage("user", userPrompt)
                )
        );

        NvidiaChatResponse chatResponse =
                client.chatCompletion(chatRequest);

        String content = extractContent(chatResponse);
        JsonNode json = parseJson(content);

        if (!json.has("overallScore")
                || json.get("overallScore").isNull()
                || !json.get("overallScore").canConvertToInt()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a valid overallScore."
            );
        }

        int overallScore = json.get("overallScore").intValue();

        if (overallScore < 0 || overallScore > 100) {
            throw new IllegalStateException(
                    "NVIDIA overallScore is out of range [0, 100]: "
                            + overallScore
            );
        }

        if (!json.has("dimensions")
                || json.get("dimensions").isNull()
                || !json.get("dimensions").isArray()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a valid dimensions array."
            );
        }

        List<QualityDimension> dimensions = new ArrayList<>();

        Set<String> requiredDimensions = Set.of(
                "Clarity",
                "Specificity",
                "Testability",
                "Consistency",
                "Atomicity"
        );

        Set<String> returnedDimensions = new HashSet<>();

        for (JsonNode dimensionNode : json.get("dimensions")) {
            if (dimensionNode == null || !dimensionNode.isObject()) {
                throw new IllegalStateException(
                        "NVIDIA quality response contained an invalid dimension."
                );
            }

            String name = extractQualityField(
                    dimensionNode,
                    "name"
            );

            String scoreField = "score";

            if (!dimensionNode.has(scoreField)
                    || dimensionNode.get(scoreField).isNull()
                    || !dimensionNode.get(scoreField).canConvertToInt()) {
                throw new IllegalStateException(
                        "NVIDIA quality dimension did not contain a valid score."
                );
            }

            int score = dimensionNode.get(scoreField).intValue();

            if (score < 0 || score > 100) {
                throw new IllegalStateException(
                        "NVIDIA quality dimension score is out of range [0, 100]: "
                                + score
                );
            }

            String finding = extractQualityField(
                    dimensionNode,
                    "finding"
            );

            String recommendation = extractQualityField(
                    dimensionNode,
                    "recommendation"
            );

            if (!requiredDimensions.contains(name)) {
                throw new IllegalStateException(
                        "NVIDIA returned an unsupported quality dimension: "
                                + name
                );
            }

            if (!returnedDimensions.add(name)) {
                throw new IllegalStateException(
                        "NVIDIA returned duplicate quality dimension: "
                                + name
                );
            }

            dimensions.add(
                    new QualityDimension(
                            name,
                            score,
                            finding,
                            recommendation
                    )
            );
        }

        if (!returnedDimensions.equals(requiredDimensions)) {
            throw new IllegalStateException(
                    "NVIDIA quality response did not contain exactly the required "
                            + "quality dimensions."
            );
        }

        BigDecimal confidence = extractConfidence(json);

        return new QualityAnalysisResponse(
                overallScore,
                dimensions,
                confidence
        );
    }

    @Override
    public DuplicateDetectionResponse detectDuplicates(
            DuplicateDetectionRequest request
    ) {
        String systemPrompt = """
                You are a software requirements duplicate detection engine.

                Compare the target software requirement against every supplied
                candidate requirement.

                Your task is to identify requirements that describe the same
                requirement intent, behavior, business rule, user action, or expected
                outcome.

                DUPLICATE DECISION RULES:

                A candidate MUST be considered a duplicate when:
                - it describes the same actor or user action,
                - on the same system, feature, component, or object,
                - with the same intended behavior or outcome,
                - and the difference is only wording, synonyms, or phrasing.

                Treat semantically equivalent verbs as equivalent when the resulting
                behavior is the same.

                For example:
                - "build a Docker image" and "create a Docker image" are duplicates.
                - "allow users to log in" and "allow users to authenticate" are
                duplicates when they describe the same login behavior.
                - "remove a user" and "delete a user" are duplicates when they
                describe the same operation.

                Requirements are NOT duplicates when they perform different
                lifecycle actions, even if they operate on the same system,
                component, technology, or feature.

                For example:
                - "build a Docker image" and "create a Docker image" are duplicates.
                - "build a Docker image" and "deploy using Docker containers" are NOT duplicates.
                - "create a Docker image" and "run a Docker container" are NOT duplicates.
                - "deploy an application" and "monitor an application" are NOT duplicates.
                - "delete a user" and "disable a user" are NOT duplicates.

                Pay particular attention to the primary action verb and intended
                outcome. Different lifecycle actions such as build, create, deploy,
                run, start, stop, update, delete, monitor, and configure should not
                be considered duplicates unless they clearly describe the same
                resulting behavior.

                For example:
                - "build a Docker image" and "deploy using Docker containers" are
                different requirements and should not automatically be considered
                duplicates.
                - "send an email" and "send an SMS" are different behaviors.

                When evaluating a candidate, compare the actual behavior and intended
                outcome rather than relying only on exact wording.

                DUPLICATE EVALUATION METHOD:

                For each candidate, first evaluate these three aspects:

                1. PRIMARY ACTION
                   The main operation performed by the user or system.
                   Examples: build, create, deploy, login, delete, update, send.

                2. TARGET OBJECT
                   The main entity, artifact, or resource being acted upon.
                   Examples: Docker image, user account, email, frontend application.

                3. INTENDED OUTCOME
                   What the requirement is ultimately trying to accomplish.

                A candidate is a duplicate only when the primary action,
                target object, and intended outcome are semantically equivalent.

                Equivalent wording and synonymous verbs are allowed when they
                produce the same behavior.

                Different lifecycle actions must remain different requirements.

                For example:

                "build a Docker image" and "create a Docker image"
                have equivalent action, object, and outcome and are duplicates.

                "build a Docker image" and "deploy the frontend using Docker"
                have different primary actions and different outcomes and are NOT
                duplicates.

                Do not use the fact that two requirements mention the same
                technology, feature, component, or domain as sufficient evidence
                of duplication.

                When uncertain, do NOT classify the candidate as a duplicate.

                For every duplicate candidate:
                - return the exact candidate requirementId,
                - provide a similarity value between 0.0 and 1.0,
                - provide a concise relationship classification,
                - explain why the requirements are duplicates.

                Use relationship values such as:
                - SIMILAR_FUNCTIONALITY
                - SAME_BEHAVIOR
                - OVERLAPPING_REQUIREMENT

                    RELATIONSHIP DEFINITIONS:

                    SAME_BEHAVIOR:
                    The candidate describes the same operation and intended outcome
                    as the target. This is a TRUE DUPLICATE.

                    SIMILAR_FUNCTIONALITY:
                    The candidate is related to the same feature, technology, or domain
                    but performs a different operation or produces a different outcome.
                    This is NOT a duplicate.

                    OVERLAPPING_REQUIREMENT:
                    The candidate shares some functionality with the target but contains
                    additional, missing, or different behavior. This is NOT automatically
                    a duplicate.

                    IMPORTANT:
                    Only return a candidate in the "duplicates" array when its relationship
                    is SAME_BEHAVIOR.

                    Do NOT return SIMILAR_FUNCTIONALITY or OVERLAPPING_REQUIREMENT in
                    the duplicates array.

                If no candidate is a duplicate:
                - duplicates must be an empty array.

                OUTPUT CONTRACT:

                Respond ONLY with a JSON object in exactly this structure:
                {
                "duplicates": [
                        {
                        "requirementId": "00000000-0000-0000-0000-000000000001",
                        "similarity": 0.98,
                        "relationship": "SAME_BEHAVIOR",
                        "reason": "Both requirements describe the same user action and intended outcome."
                        }
                ],
                "confidence": 0.95
                }

                IMPORTANT OUTPUT RULES:

                The duplicate identifier field MUST be named exactly:
                "requirementId"

                Never use:
                "candidateRequirementId"

                Only return requirement IDs that appear in the supplied candidate
                requirements.

                similarity must be a number between 0.0 and 1.0.
                confidence must be a number between 0.0 and 1.0.

                Do not include markdown.
                Do not include any text outside the JSON object.
                """;

        StringBuilder userPrompt = new StringBuilder();

        userPrompt.append("Target requirement:\n")
                .append(request.targetRequirement())
                .append("\n\nCandidate requirements:\n");

        if (request.candidateRequirements() == null
                || request.candidateRequirements().isEmpty()) {
            userPrompt.append("No candidate requirements were supplied.");
        } else {
            for (RequirementCandidate candidate : request.candidateRequirements()) {
                userPrompt.append("\nCandidate ID: ")
                        .append(candidate.requirementId())
                        .append("\nCandidate text: ")
                        .append(candidate.text())
                        .append("\n");
            }
        }

        NvidiaChatRequest chatRequest = new NvidiaChatRequest(
                resolveModel(),
                List.of(
                        new NvidiaChatMessage("system", systemPrompt),
                        new NvidiaChatMessage("user", userPrompt.toString())
                )
        );

        NvidiaChatResponse chatResponse =
                client.chatCompletion(chatRequest);

        String content = extractContent(chatResponse);
        JsonNode json = parseJson(content);

        if (!json.has("duplicates")
                || json.get("duplicates").isNull()
                || !json.get("duplicates").isArray()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a valid duplicates array."
            );
        }

        List<DuplicateCandidate> duplicates = new ArrayList<>();

        Set<UUID> candidateIds = new HashSet<>();

        if (request.candidateRequirements() != null) {
            for (RequirementCandidate candidate :
                    request.candidateRequirements()) {

                if (candidate != null
                        && candidate.requirementId() != null) {
                    candidateIds.add(candidate.requirementId());
                }
            }
        }

        Set<UUID> returnedIds = new HashSet<>();

        for (JsonNode duplicateNode : json.get("duplicates")) {
            if (duplicateNode == null || !duplicateNode.isObject()) {
                throw new IllegalStateException(
                        "NVIDIA duplicate response contained an invalid duplicate."
                );
            }

            if (!duplicateNode.has("requirementId")
                    || duplicateNode.get("requirementId").isNull()
                    || duplicateNode.get("requirementId").asText("").isBlank()) {
                throw new IllegalStateException(
                        "NVIDIA duplicate response did not contain a requirementId."
                );
            }

            UUID requirementId;

            try {
                requirementId = UUID.fromString(
                        duplicateNode.get("requirementId").asText().trim()
                );
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "NVIDIA duplicate response contained an invalid requirementId.",
                        exception
                );
            }

            if (!candidateIds.contains(requirementId)) {
                throw new IllegalStateException(
                        "NVIDIA returned a requirementId that was not supplied "
                                + "as a candidate: " + requirementId
                );
            }

            if (!returnedIds.add(requirementId)) {
                throw new IllegalStateException(
                        "NVIDIA returned duplicate requirementId: "
                                + requirementId
                );
            }

            if (!duplicateNode.has("similarity")
                    || duplicateNode.get("similarity").isNull()) {
                throw new IllegalStateException(
                        "NVIDIA duplicate response did not contain a similarity value."
                );
            }

            BigDecimal similarity;

            try {
                similarity = duplicateNode.get("similarity").decimalValue();
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "NVIDIA duplicate response contains an invalid similarity value.",
                        exception
                );
            }

            if (similarity.compareTo(BigDecimal.ZERO) < 0
                    || similarity.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalStateException(
                        "NVIDIA similarity value is out of range [0, 1]: "
                                + similarity
                );
            }

            String relationship = extractDuplicateField(
                    duplicateNode,
                    "relationship"
            );

            String reason = extractDuplicateField(
                    duplicateNode,
                    "reason"
            );

            if (!"SAME_BEHAVIOR".equals(relationship)) {
                throw new IllegalStateException(
                        "NVIDIA duplicate response contained a non-duplicate relationship: " + relationship
                );
            }

            duplicates.add(
                    new DuplicateCandidate(
                            requirementId,
                            similarity,
                            relationship,
                            reason
                    )
            );
        }

        BigDecimal confidence = extractConfidence(json);

        return new DuplicateDetectionResponse(
                duplicates,
                confidence
        );
    }

    @Override
    public ConflictDetectionResponse detectConflicts(
            ConflictDetectionRequest request
    ) {
        String systemPrompt = """
                You are a software requirements conflict detection engine.

                Compare the target software requirement against the supplied
                candidate requirements.

                Identify candidates that conflict with the target requirement.

                A conflict exists when two requirements impose incompatible or
                mutually exclusive behavior, rules, constraints, states, or
                conditions.

                Examples of conflicts include:
                - one requirement permits an action while another prohibits it
                - two requirements specify incompatible values or limits
                - two requirements require mutually exclusive system behavior
                - one requirement requires a condition that another requirement
                explicitly forbids

                Do not mark a candidate as a conflict merely because the
                requirements are different, related, or describe different
                features.

                For every conflict:
                - return the exact candidate requirementId
                - provide a concise conflictType
                - provide a severity
                - explain why the requirements conflict
                - provide a suggestion for resolving or clarifying the conflict

                Use conflictType values that describe the conflict, such as:
                - LOGICAL_CONTRADICTION
                - INCOMPATIBLE_BEHAVIOR
                - CONSTRAINT_CONFLICT
                - POLICY_CONFLICT

                Use severity values:
                - LOW
                - MEDIUM
                - HIGH

                If no candidate conflicts with the target requirement:
                - conflicts must be an empty array

                Respond ONLY with a JSON object in exactly this structure:
                {
                "conflicts": [
                    {
                    "requirementId": "00000000-0000-0000-0000-000000000001",
                    "conflictType": "LOGICAL_CONTRADICTION",
                    "severity": "HIGH",
                    "reason": "The target permits an action that the candidate explicitly prohibits.",
                    "suggestion": "Clarify which rule takes precedence."
                    }
                ],
                "confidence": 0.90
                }

                confidence must be a number between 0.0 and 1.0.

                Only return requirement IDs that appear in the supplied candidate
                requirements.

                Do not include markdown.
                Do not include any text outside the JSON object.
                """;

        StringBuilder userPrompt = new StringBuilder();

        userPrompt.append("Target requirement:\n")
                .append(request.targetRequirement())
                .append("\n\nCandidate requirements:\n");

        if (request.candidateRequirements() == null
                || request.candidateRequirements().isEmpty()) {
            userPrompt.append("No candidate requirements were supplied.");
        } else {
            for (RequirementCandidate candidate :
                    request.candidateRequirements()) {
                userPrompt.append("\nCandidate ID: ")
                        .append(candidate.requirementId())
                        .append("\nCandidate text: ")
                        .append(candidate.text())
                        .append("\n");
            }
        }

        NvidiaChatRequest chatRequest = new NvidiaChatRequest(
                resolveModel(),
                List.of(
                        new NvidiaChatMessage("system", systemPrompt),
                        new NvidiaChatMessage("user", userPrompt.toString())
                )
        );

        NvidiaChatResponse chatResponse =
                client.chatCompletion(chatRequest);

        String content = extractContent(chatResponse);
        JsonNode json = parseJson(content);

        if (!json.has("conflicts")
                || json.get("conflicts").isNull()
                || !json.get("conflicts").isArray()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a valid conflicts array."
            );
        }

        List<ConflictFinding> conflicts = new ArrayList<>();

        Set<UUID> candidateIds = new HashSet<>();

        if (request.candidateRequirements() != null) {
            for (RequirementCandidate candidate :
                    request.candidateRequirements()) {

                if (candidate != null
                        && candidate.requirementId() != null) {
                    candidateIds.add(candidate.requirementId());
                }
            }
        }

        Set<UUID> returnedIds = new HashSet<>();

        for (JsonNode conflictNode : json.get("conflicts")) {
            if (conflictNode == null || !conflictNode.isObject()) {
                throw new IllegalStateException(
                        "NVIDIA conflict response contained an invalid conflict."
                );
            }

            if (!conflictNode.has("requirementId")
                    || conflictNode.get("requirementId").isNull()
                    || conflictNode.get("requirementId").asText("").isBlank()) {
                throw new IllegalStateException(
                        "NVIDIA conflict response did not contain a requirementId."
                );
            }

            UUID requirementId;

            try {
                requirementId = UUID.fromString(
                        conflictNode.get("requirementId").asText().trim()
                );
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "NVIDIA conflict response contained an invalid requirementId.",
                        exception
                );
            }

            if (!candidateIds.contains(requirementId)) {
                throw new IllegalStateException(
                        "NVIDIA returned a requirementId that was not supplied "
                                + "as a candidate: " + requirementId
                );
            }

            if (!returnedIds.add(requirementId)) {
                throw new IllegalStateException(
                        "NVIDIA returned duplicate requirementId: "
                                + requirementId
                );
            }

            String conflictType = extractConflictField(
                    conflictNode,
                    "conflictType"
            );

            String severity = extractConflictField(
                    conflictNode,
                    "severity"
            );

            String reason = extractConflictField(
                    conflictNode,
                    "reason"
            );

            String suggestion = extractConflictField(
                    conflictNode,
                    "suggestion"
            );

            conflicts.add(
                    new ConflictFinding(
                            requirementId,
                            conflictType,
                            severity,
                            reason,
                            suggestion
                    )
            );
        }

        BigDecimal confidence = extractConfidence(json);

        return new ConflictDetectionResponse(
                conflicts,
                confidence
        );
    }

    @Override
    public AIAnalysisResult analyze(Requirement requirement) {
        if (requirement == null) {
                throw new IllegalArgumentException("Requirement must not be null.");
        }

        String title = requirement.getTitle() == null
                ? ""
                : requirement.getTitle();

        String description = requirement.getDescription() == null
                ? ""
                : requirement.getDescription();

        String systemPrompt = """
                You are a software requirements analysis engine.

                Analyze the supplied software requirement and return ONLY
                a valid JSON object.

                The JSON must contain exactly these fields:
                {
                "summary": "brief analysis summary",
                "ambiguityScore": 0.0,
                "completenessScore": 0.0,
                "qualityScore": 0.0,
                "suggestions": "improvement suggestions"
                }

                Score fields must be numbers between 0.0 and 100.0.

                ambiguityScore:
                0 means completely clear and precise.
                100 means highly ambiguous or vague.

                completenessScore:
                0 means very incomplete.
                100 means complete enough for implementation and testing.

                qualityScore:
                0 means very poor quality.
                100 means excellent requirement quality.

                Do not include markdown.
                Do not include any text outside the JSON object.
                """;

        String userPrompt = """
                Analyze this software requirement.

                Title:
                %s

                Description:
                %s
                """.formatted(title, description);

        NvidiaChatRequest chatRequest = new NvidiaChatRequest(
                resolveModel(),
                List.of(
                        new NvidiaChatMessage("system", systemPrompt),
                        new NvidiaChatMessage("user", userPrompt)
                )
        );

        NvidiaChatResponse chatResponse =
                client.chatCompletion(chatRequest);

        String content = extractContent(chatResponse);
        JsonNode json = parseJson(content);

        String summary = extractRequiredString(json, "summary");
        BigDecimal ambiguityScore =
                extractScore(json, "ambiguityScore");
        BigDecimal completenessScore =
                extractScore(json, "completenessScore");
        BigDecimal qualityScore =
                extractScore(json, "qualityScore");
        String suggestions = extractRequiredString(json, "suggestions");

        return new AIAnalysisResult(
                summary,
                ambiguityScore,
                completenessScore,
                qualityScore,
                suggestions
        );
        }
    private String resolveModel() {
        if (model == null || model.isBlank()) {
            throw new IllegalStateException(
                "NVIDIA analysis model is not configured."
            );
        }
        return model;
    }

    private String extractContent(NvidiaChatResponse response) {
        if (response == null) {
            throw new IllegalStateException(
                    "NVIDIA returned an empty response."
            );
        }

        if (response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain any choices."
            );
        }

        NvidiaChatResponse.Choice firstChoice = response.choices().get(0);

        if (firstChoice.message() == null
                || firstChoice.message().content() == null
                || firstChoice.message().content().isBlank()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain message content."
            );
        }

        return firstChoice.message().content().trim();
    }

    private JsonNode parseJson(String content) {
        // Strip markdown code fences if present
        String cleaned = content;
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        try {
            return objectMapper.readTree(cleaned);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "NVIDIA response is not valid JSON: " + content, e
            );
        }
    }

    private BigDecimal extractScore(
                JsonNode json,
                String field
        ) {
        if (!json.has(field)
                || json.get(field).isNull()
                || !json.get(field).isNumber()) {
                throw new IllegalStateException(
                        "NVIDIA response did not contain a numeric "
                                + field + "."
                );
        }

        BigDecimal score = json.get(field).decimalValue();

        if (score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(new BigDecimal("100.00")) > 0) {
                throw new IllegalStateException(
                        "NVIDIA response contained an invalid "
                                + field + ": " + score
                );
        }

        return score;
        }

    private String extractRequiredString(JsonNode json, String field) {
        if (!json.has(field)
                || json.get(field).isNull()
                || json.get(field).asText("").isBlank()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a " + field + "."
            );
        }
        return json.get(field).asText().trim().toUpperCase();
    }

    private String extractFindingField(
            JsonNode findingNode,
            String field
    ) {
        if (findingNode == null
                || !findingNode.has(field)
                || findingNode.get(field).isNull()
                || findingNode.get(field).asText("").isBlank()) {
            throw new IllegalStateException(
                    "NVIDIA ambiguity finding did not contain a "
                            + field + "."
            );
        }
        return findingNode.get(field).asText().trim();
    }

    private String extractCompletenessField(
            JsonNode node,
            String field
    ) {
        if (node == null
                || !node.has(field)
                || node.get(field).isNull()
                || node.get(field).asText("").isBlank()) {
            throw new IllegalStateException(
                    "NVIDIA completeness response did not contain a "
                            + field + "."
            );
        }

        return node.get(field).asText().trim();
    }

    private String extractQualityField(
            JsonNode node,
            String field
    ) {
        if (node == null
                || !node.has(field)
                || node.get(field).isNull()
                || node.get(field).asText("").isBlank()) {
            throw new IllegalStateException(
                    "NVIDIA quality dimension did not contain a "
                            + field + "."
            );
        }

        return node.get(field).asText().trim();
    }

    private String extractDuplicateField(
            JsonNode node,
            String field
    ) {
        if (node == null
                || !node.has(field)
                || node.get(field).isNull()
                || node.get(field).asText("").isBlank()) {
            throw new IllegalStateException(
                    "NVIDIA duplicate response did not contain a "
                            + field + "."
            );
        }

        return node.get(field).asText().trim();
    }

    private String extractConflictField(
            JsonNode node,
            String field
    ) {
        if (node == null
                || !node.has(field)
                || node.get(field).isNull()
                || node.get(field).asText("").isBlank()) {
            throw new IllegalStateException(
                    "NVIDIA conflict response did not contain a "
                            + field + "."
            );
        }

        return node.get(field).asText().trim();
    }


    private BigDecimal extractConfidence(JsonNode json) {
        if (!json.has("confidence") || json.get("confidence").isNull()) {
            throw new IllegalStateException(
                    "NVIDIA response did not contain a confidence value."
            );
        }

        BigDecimal confidence;
        try {
            confidence = json.get("confidence").decimalValue();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "NVIDIA response contains an invalid confidence value.", e
            );
        }

        if (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException(
                    "NVIDIA confidence value is out of range [0, 1]: " + confidence
            );
        }

        return confidence;
    }
}
