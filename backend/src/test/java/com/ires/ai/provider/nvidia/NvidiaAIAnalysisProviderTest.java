package com.ires.ai.provider.nvidia;

import com.ires.ai.dto.analysis.AmbiguityFinding;
import com.ires.ai.dto.analysis.AmbiguityRequest;
import com.ires.ai.dto.analysis.AmbiguityResponse;
import com.ires.ai.dto.analysis.ClassificationRequest;
import com.ires.ai.dto.analysis.ClassificationResponse;
import com.ires.ai.dto.analysis.CompletenessRequest;
import com.ires.ai.dto.analysis.CompletenessResponse;
import com.ires.ai.dto.analysis.DuplicateCandidate;
import com.ires.ai.dto.analysis.DuplicateDetectionRequest;
import com.ires.ai.dto.analysis.DuplicateDetectionResponse;
import com.ires.ai.dto.analysis.RequirementCandidate;
import com.ires.ai.dto.analysis.MissingInformation;
import com.ires.ai.dto.analysis.QualityAnalysisRequest;
import com.ires.ai.dto.analysis.QualityAnalysisResponse;
import com.ires.ai.dto.analysis.QualityDimension;
import com.ires.ai.dto.analysis.ConflictDetectionRequest;
import com.ires.ai.dto.analysis.ConflictDetectionResponse;
import com.ires.ai.dto.analysis.ConflictFinding;
import com.ires.ai.provider.nvidia.dto.NvidiaChatRequest;
import com.ires.ai.provider.nvidia.dto.NvidiaChatResponse;
import com.ires.ai.service.AIAnalysisProvider;
import com.ires.requirement.entity.Requirement;
import com.ires.requirement.entity.RequirementPriority;
import com.ires.requirement.entity.RequirementStatus;
import com.ires.requirement.entity.RequirementType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoInteractions;

class NvidiaAIAnalysisProviderTest {

    private NvidiaApiClient client;
    private NvidiaAIAnalysisProvider provider;

    @BeforeEach
    void setUp() {
        client = mock(NvidiaApiClient.class);
        provider = new NvidiaAIAnalysisProvider(client, "test-model");
    }

    @Test
    void legacyAnalyzeReturnsAnalysisResult() {
        NvidiaChatResponse response = chatResponse("""
                {
                "summary": "The requirement describes a clear login capability.",
                "ambiguityScore": 15.0,
                "completenessScore": 85.0,
                "qualityScore": 90.0,
                "suggestions": "Define the expected behavior for failed login attempts."
                }
                """);

        when(client.chatCompletion(any())).thenReturn(response);

        Requirement requirement = new Requirement(
                null,
                "User Login",
                "The system shall allow users to log in using their registered email and password.",
                RequirementType.FUNCTIONAL,
                RequirementPriority.MEDIUM,
                RequirementStatus.DRAFT,
                "TEST",
                null,
                null
        );

        AIAnalysisProvider.AIAnalysisResult result =
                provider.analyze(requirement);

        assertEquals(
                "THE REQUIREMENT DESCRIBES A CLEAR LOGIN CAPABILITY.",
                result.summary()
        );
        assertEquals(new BigDecimal("15.0"), result.ambiguityScore());
        assertEquals(new BigDecimal("85.0"), result.completenessScore());
        assertEquals(new BigDecimal("90.0"), result.qualityScore());
        assertEquals(
                "DEFINE THE EXPECTED BEHAVIOR FOR FAILED LOGIN ATTEMPTS.",
                result.suggestions()
        );

        verify(client).chatCompletion(any(NvidiaChatRequest.class));
        }

    @Test
    void classifyReturnsValidClassification() {
        NvidiaChatResponse response = chatResponse(
                "{\"classification\": \"FUNCTIONAL\", \"confidence\": 0.94, \"reason\": \"Describes system behavior.\"}"
        );

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(),
                "The system shall allow users to log in."
        );

        ClassificationResponse result = provider.classify(request);

        assertEquals("FUNCTIONAL", result.classification());
        assertEquals(new BigDecimal("0.94"), result.confidence());
        assertEquals("Describes system behavior.", result.reason());

        verify(client).chatCompletion(any(NvidiaChatRequest.class));
    }

    @Test
    void classifyHandlesAllValidClassifications() {
        for (String category : List.of(
                "FUNCTIONAL", "NON_FUNCTIONAL", "BUSINESS", "TECHNICAL", "UNCLEAR"
        )) {
            NvidiaChatResponse response = chatResponse(
                    "{\"classification\": \"" + category + "\", \"confidence\": 0.85, \"reason\": \"Test reason.\"}"
            );

            when(client.chatCompletion(any())).thenReturn(response);

            ClassificationResponse result = provider.classify(
                    new ClassificationRequest(UUID.randomUUID(), "Some requirement.")
            );

            assertEquals(category, result.classification());
        }
    }

    @Test
    void classifyUsesConfiguredModel() {
        NvidiaChatResponse response = chatResponse(
                "{\"classification\": \"FUNCTIONAL\", \"confidence\": 0.90, \"reason\": \"Test.\"}"
        );

        when(client.chatCompletion(any())).thenReturn(response);

        provider.classify(new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        ));

        verify(client).chatCompletion(argThat(request ->
                "test-model".equals(request.model())
        ));
    }

    @Test
    void classifyRejectsMissingModelConfiguration() {
        NvidiaAIAnalysisProvider providerNoModel =
                new NvidiaAIAnalysisProvider(client, "");

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(),
                "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> providerNoModel.classify(request)
        );

        assertEquals(
                "NVIDIA analysis model is not configured.",
                exception.getMessage()
        );

        verifyNoInteractions(client);
}

    @Test
    void classifyRejectsNullResponse() {
        when(client.chatCompletion(any())).thenReturn(null);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertEquals(
                "NVIDIA returned an empty response.",
                exception.getMessage()
        );
    }

    @Test
    void classifyRejectsEmptyChoices() {
        NvidiaChatResponse response = new NvidiaChatResponse(List.of());

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertEquals(
                "NVIDIA response did not contain any choices.",
                exception.getMessage()
        );
    }

    @Test
    void classifyRejectsNullChoices() {
        NvidiaChatResponse response = new NvidiaChatResponse(null);

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertEquals(
                "NVIDIA response did not contain any choices.",
                exception.getMessage()
        );
    }

    @Test
    void classifyRejectsBlankMessageContent() {
        NvidiaChatResponse response = new NvidiaChatResponse(
                List.of(new NvidiaChatResponse.Choice(
                        new NvidiaChatResponse.Message("assistant", "   ")
                ))
        );

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertEquals(
                "NVIDIA response did not contain message content.",
                exception.getMessage()
        );
    }

    @Test
    void classifyRejectsInvalidJson() {
        NvidiaChatResponse response = chatResponse("not valid json at all");

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertTrue(exception.getMessage().contains("not valid JSON"));
    }

    @Test
    void classifyRejectsInvalidClassification() {
        NvidiaChatResponse response = chatResponse(
                "{\"classification\": \"INVALID_TYPE\", \"confidence\": 0.85, \"reason\": \"Test.\"}"
        );

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertTrue(exception.getMessage().contains("invalid classification"));
    }

    @Test
    void classifyRejectsMissingClassification() {
        NvidiaChatResponse response = chatResponse(
                "{\"confidence\": 0.85, \"reason\": \"Test.\"}"
        );

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertTrue(exception.getMessage().contains("classification"));
    }

    @Test
    void classifyRejectsMissingConfidence() {
        NvidiaChatResponse response = chatResponse(
                "{\"classification\": \"FUNCTIONAL\", \"reason\": \"Test.\"}"
        );

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertTrue(exception.getMessage().contains("confidence"));
    }

    @Test
    void classifyRejectsMissingReason() {
        NvidiaChatResponse response = chatResponse(
                "{\"classification\": \"FUNCTIONAL\", \"confidence\": 0.90, \"reason\": \"\"}"
        );

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertTrue(exception.getMessage().contains("reason"));
    }

    @Test
    void classifyRejectsConfidenceOutOfRange() {
        NvidiaChatResponse response = chatResponse(
                "{\"classification\": \"FUNCTIONAL\", \"confidence\": 1.5, \"reason\": \"Test.\"}"
        );

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationRequest request = new ClassificationRequest(
                UUID.randomUUID(), "Some requirement."
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(request)
        );

        assertTrue(exception.getMessage().contains("out of range"));
    }

    @Test
    void classifyStripsMarkdownCodeFences() {
        NvidiaChatResponse response = chatResponse(
                "```json\n{\"classification\": \"BUSINESS\", \"confidence\": 0.88, \"reason\": \"Business rule.\"}\n```"
        );

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationResponse result = provider.classify(
                new ClassificationRequest(UUID.randomUUID(), "Some requirement.")
        );

        assertEquals("BUSINESS", result.classification());
        assertEquals(new BigDecimal("0.88"), result.confidence());
        assertEquals("Business rule.", result.reason());
    }

    @Test
    void classifyHandlesLowercaseClassificationByUppercasing() {
        NvidiaChatResponse response = chatResponse(
                "{\"classification\": \"functional\", \"confidence\": 0.90, \"reason\": \"Test reason.\"}"
        );

        when(client.chatCompletion(any())).thenReturn(response);

        ClassificationResponse result = provider.classify(
                new ClassificationRequest(UUID.randomUUID(), "Some requirement.")
        );

        assertEquals("FUNCTIONAL", result.classification());
    }
/* ------------------------------------------------------------------------------------------------------------------------------------------------------------*/
    @Test
    void detectAmbiguityReturnsFindingsForAmbiguousRequirement() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                  "hasAmbiguity": true,
                  "findings": [
                    {
                      "text": "respond quickly",
                      "reason": "The word 'quickly' does not define a measurable response time.",
                      "suggestion": "Specify the maximum response time, for example within 2 seconds."
                    }
                  ],
                  "confidence": 0.89
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        AmbiguityRequest request = new AmbiguityRequest(
                UUID.randomUUID(),
                "The system shall respond quickly to user requests."
        );

        AmbiguityResponse result = provider.detectAmbiguity(request);

        assertTrue(result.hasAmbiguity());
        assertEquals(1, result.findings().size());

        AmbiguityFinding finding = result.findings().get(0);

        assertEquals("respond quickly", finding.text());
        assertEquals(
                "The word 'quickly' does not define a measurable response time.",
                finding.reason()
        );
        assertEquals(
                "Specify the maximum response time, for example within 2 seconds.",
                finding.suggestion()
        );
        assertEquals(new BigDecimal("0.89"), result.confidence());

        verify(client).chatCompletion(any(NvidiaChatRequest.class));
    }

    @Test
    void detectAmbiguityReturnsNoFindingsForClearRequirement() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                  "hasAmbiguity": false,
                  "findings": [],
                  "confidence": 0.95
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        AmbiguityRequest request = new AmbiguityRequest(
                UUID.randomUUID(),
                "The system shall return a response within 2 seconds."
        );

        AmbiguityResponse result = provider.detectAmbiguity(request);

        assertFalse(result.hasAmbiguity());
        assertTrue(result.findings().isEmpty());
        assertEquals(new BigDecimal("0.95"), result.confidence());
    }

    @Test
    void detectAmbiguityMapsMultipleFindings() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                  "hasAmbiguity": true,
                  "findings": [
                    {
                      "text": "quickly",
                      "reason": "Response time is not measurable.",
                      "suggestion": "Specify a maximum response time."
                    },
                    {
                      "text": "user-friendly",
                      "reason": "The term does not define measurable usability criteria.",
                      "suggestion": "Define concrete usability requirements."
                    }
                  ],
                  "confidence": 0.91
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        AmbiguityResponse result = provider.detectAmbiguity(
                new AmbiguityRequest(
                        UUID.randomUUID(),
                        "The system shall respond quickly and provide a user-friendly interface."
                )
        );

        assertTrue(result.hasAmbiguity());
        assertEquals(2, result.findings().size());

        assertEquals("quickly", result.findings().get(0).text());
        assertEquals("user-friendly", result.findings().get(1).text());

        assertEquals(new BigDecimal("0.91"), result.confidence());
    }

    @Test
    void detectAmbiguityRejectsMissingHasAmbiguity() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                  "findings": [],
                  "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.detectAmbiguity(
                        new AmbiguityRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(exception.getMessage().contains("hasAmbiguity"));
    }

    @Test
    void detectAmbiguityRejectsMissingConfidence() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                  "hasAmbiguity": false,
                  "findings": []
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.detectAmbiguity(
                        new AmbiguityRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(exception.getMessage().contains("confidence"));
    }

    @Test
    void detectAmbiguityRejectsIncompleteFinding() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                  "hasAmbiguity": true,
                  "findings": [
                    {
                      "text": "quickly",
                      "reason": "Response time is not measurable."
                    }
                  ],
                  "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.detectAmbiguity(
                        new AmbiguityRequest(
                                UUID.randomUUID(),
                                "The system shall respond quickly."
                        )
                )
        );

        assertTrue(exception.getMessage().contains("suggestion"));
    }

    @Test
    void detectAmbiguityRejectsConfidenceOutOfRange() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                  "hasAmbiguity": true,
                  "findings": [
                    {
                      "text": "quickly",
                      "reason": "Response time is not measurable.",
                      "suggestion": "Specify a maximum response time."
                    }
                  ],
                  "confidence": 1.5
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.detectAmbiguity(
                        new AmbiguityRequest(
                                UUID.randomUUID(),
                                "The system shall respond quickly."
                        )
                )
        );

        assertTrue(exception.getMessage().contains("out of range"));
    }

    @Test
    void detectAmbiguityRejectsFalseWithFindings() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                  "hasAmbiguity": false,
                  "findings": [
                    {
                      "text": "quickly",
                      "reason": "Response time is not measurable.",
                      "suggestion": "Specify a maximum response time."
                    }
                  ],
                  "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.detectAmbiguity(
                        new AmbiguityRequest(
                                UUID.randomUUID(),
                                "The system shall respond quickly."
                        )
                )
        );
       assertTrue(exception.getMessage().contains("hasAmbiguity is false"));
    }

    @Test
    void detectAmbiguityRejectsTrueWithoutFindings() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                  "hasAmbiguity": true,
                  "findings": [],
                  "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.detectAmbiguity(
                        new AmbiguityRequest(
                                UUID.randomUUID(),
                                "The system shall respond quickly."
                        )
                )
        );

        assertTrue(exception.getMessage().contains("hasAmbiguity is true"));
    }

    @Test
    void detectAmbiguityRejectsInvalidJson() {
        NvidiaChatResponse response =
                chatResponse("not valid json at all");

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.detectAmbiguity(
                        new AmbiguityRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(exception.getMessage().contains("not valid JSON"));
    }

    @Test
    void detectAmbiguityStripsMarkdownCodeFences() {
        NvidiaChatResponse response = chatResponse(
                """
                ```json
                {
                  "hasAmbiguity": true,
                  "findings": [
                    {
                      "text": "quickly",
                      "reason": "Response time is not measurable.",
                      "suggestion": "Specify a maximum response time."
                    }
                  ],
                  "confidence": 0.88
                }
                ```
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        AmbiguityResponse result = provider.detectAmbiguity(
                new AmbiguityRequest(
                        UUID.randomUUID(),
                        "The system shall respond quickly."
                )
        );

        assertTrue(result.hasAmbiguity());
        assertEquals(1, result.findings().size());
        assertEquals("quickly", result.findings().get(0).text());
        assertEquals(new BigDecimal("0.88"), result.confidence());
    }

        @Test
        void analyzeCompletenessReturnsMissingInformation() {
        NvidiaChatResponse response = chatResponse(
                """
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
                "confidence": 0.86
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        CompletenessResponse result = provider.analyzeCompleteness(
                new CompletenessRequest(
                        UUID.randomUUID(),
                        "The system shall process uploaded files."
                )
        );

        assertFalse(result.isComplete());
        assertEquals(1, result.missingInformation().size());

        MissingInformation missing = result.missingInformation().get(0);

        assertEquals("Error Handling", missing.aspect());
        assertEquals(
                "The requirement does not specify what should happen when processing fails.",
                missing.description()
        );

        assertEquals(1, result.clarificationQuestions().size());
        assertEquals(
                "What should the system do when processing fails?",
                result.clarificationQuestions().get(0)
        );

        assertEquals(new BigDecimal("0.86"), result.confidence());

        verify(client).chatCompletion(any(NvidiaChatRequest.class));
        }

        @Test
        void analyzeCompletenessReturnsCompleteRequirement() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": true,
                "missingInformation": [],
                "clarificationQuestions": [],
                "confidence": 0.95
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        CompletenessResponse result = provider.analyzeCompleteness(
                new CompletenessRequest(
                        UUID.randomUUID(),
                        "The system shall return a response within 2 seconds and display an error message if processing fails."
                )
        );

        assertTrue(result.isComplete());
        assertTrue(result.missingInformation().isEmpty());
        assertTrue(result.clarificationQuestions().isEmpty());
        assertEquals(new BigDecimal("0.95"), result.confidence());
        }

        @Test
        void analyzeCompletenessMapsMultipleMissingInformationItems() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": false,
                "missingInformation": [
                        {
                        "aspect": "Input Validation",
                        "description": "The requirement does not define valid input constraints."
                        },
                        {
                        "aspect": "Error Handling",
                        "description": "The requirement does not define behavior when processing fails."
                        }
                ],
                "clarificationQuestions": [
                        "What input values are valid?",
                        "What should happen when processing fails?"
                ],
                "confidence": 0.91
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        CompletenessResponse result = provider.analyzeCompleteness(
                new CompletenessRequest(
                        UUID.randomUUID(),
                        "The system shall process user input."
                )
        );

        assertFalse(result.isComplete());
        assertEquals(2, result.missingInformation().size());
        assertEquals(2, result.clarificationQuestions().size());

        assertEquals(
                "Input Validation",
                result.missingInformation().get(0).aspect()
        );

        assertEquals(
                "Error Handling",
                result.missingInformation().get(1).aspect()
        );

        assertEquals(
                "What input values are valid?",
                result.clarificationQuestions().get(0)
        );

        assertEquals(
                "What should happen when processing fails?",
                result.clarificationQuestions().get(1)
        );
        }

        @Test
        void analyzeCompletenessRejectsMissingIsComplete() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "missingInformation": [],
                "clarificationQuestions": [],
                "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(exception.getMessage().contains("isComplete"));
        }

        @Test
        void analyzeCompletenessRejectsInvalidMissingInformation() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": false,
                "missingInformation": "Error handling",
                "clarificationQuestions": [
                        "What should happen on failure?"
                ],
                "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("missingInformation")
        );
        }
        @Test
        void analyzeCompletenessRejectsIncompleteMissingInformation() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": false,
                "missingInformation": [
                        {
                        "aspect": "Error Handling"
                        }
                ],
                "clarificationQuestions": [
                        "What should happen on failure?"
                ],
                "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(exception.getMessage().contains("description"));
        }

        @Test
        void analyzeCompletenessRejectsInvalidClarificationQuestion() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": false,
                "missingInformation": [
                        {
                        "aspect": "Error Handling",
                        "description": "Failure behavior is not specified."
                        }
                ],
                "clarificationQuestions": [
                        ""
                ],
                "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("clarification question")
        );
        }

        @Test
        void analyzeCompletenessRejectsMissingClarificationQuestions() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": false,
                "missingInformation": [
                        {
                        "aspect": "Error Handling",
                        "description": "Failure behavior is not specified."
                        }
                ],
                "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("clarificationQuestions")
        );
        }

        @Test
        void analyzeCompletenessRejectsCompleteWithMissingInformation() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": true,
                "missingInformation": [
                        {
                        "aspect": "Error Handling",
                        "description": "Failure behavior is not specified."
                        }
                ],
                "clarificationQuestions": [],
                "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("isComplete is true")
        );
        }

        @Test
        void analyzeCompletenessRejectsCompleteWithClarificationQuestions() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": true,
                "missingInformation": [],
                "clarificationQuestions": [
                        "What should happen on failure?"
                ],
                "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("isComplete is true")
        );
        }

        @Test
        void analyzeCompletenessRejectsIncompleteWithoutMissingInformation() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": false,
                "missingInformation": [],
                "clarificationQuestions": [
                        "What should happen on failure?"
                ],
                "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("isComplete is false")
        );
        }

        @Test
        void analyzeCompletenessRejectsIncompleteWithoutClarificationQuestions() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": false,
                "missingInformation": [
                        {
                        "aspect": "Error Handling",
                        "description": "Failure behavior is not specified."
                        }
                ],
                "clarificationQuestions": [],
                "confidence": 0.90
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("isComplete is false")
        );
        }

        @Test
        void analyzeCompletenessRejectsConfidenceOutOfRange() {
        NvidiaChatResponse response = chatResponse(
                """
                {
                "isComplete": true,
                "missingInformation": [],
                "clarificationQuestions": [],
                "confidence": 1.5
                }
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("out of range")
        );
        }

        @Test
        void analyzeCompletenessRejectsInvalidJson() {
        NvidiaChatResponse response =
                chatResponse("not valid json at all");

        when(client.chatCompletion(any())).thenReturn(response);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.analyzeCompleteness(
                        new CompletenessRequest(
                                UUID.randomUUID(),
                                "Some requirement."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("not valid JSON")
        );
        }

        @Test
        void analyzeCompletenessStripsMarkdownCodeFences() {
        NvidiaChatResponse response = chatResponse(
                """
                ```json
                {
                "isComplete": true,
                "missingInformation": [],
                "clarificationQuestions": [],
                "confidence": 0.88
                }
                ```
                """
        );

        when(client.chatCompletion(any())).thenReturn(response);

        CompletenessResponse result = provider.analyzeCompleteness(
                new CompletenessRequest(
                        UUID.randomUUID(),
                        "The system shall return a response within 2 seconds."
                )
        );

        assertTrue(result.isComplete());
        assertTrue(result.missingInformation().isEmpty());
        assertTrue(result.clarificationQuestions().isEmpty());
        assertEquals(new BigDecimal("0.88"), result.confidence());
        }

        @Test
        void analyzeQualityReturnsValidQualityAnalysis() {
                NvidiaChatResponse response = chatResponse(
                        """
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
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                QualityAnalysisResponse result = provider.analyzeQuality(
                        new QualityAnalysisRequest(
                                UUID.randomUUID(),
                                "The system shall process user requests."
                        )
                );

                assertEquals(82, result.overallScore());
                assertEquals(5, result.dimensions().size());
                assertEquals(new BigDecimal("0.91"), result.confidence());

                assertEquals("Clarity", result.dimensions().get(0).name());
                assertEquals(85, result.dimensions().get(0).score());
                assertEquals(
                        "The requirement is mostly clear.",
                        result.dimensions().get(0).finding()
                );
                assertEquals(
                        "Replace vague terms with measurable criteria.",
                        result.dimensions().get(0).recommendation()
                );

                verify(client).chatCompletion(any(NvidiaChatRequest.class));
        }

        @Test
        void analyzeQualityMapsAllRequiredDimensions() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 75,
                        "dimensions": [
                        {
                        "name": "Clarity",
                        "score": 70,
                        "finding": "Finding 1",
                        "recommendation": "Recommendation 1"
                        },
                        {
                        "name": "Specificity",
                        "score": 72,
                        "finding": "Finding 2",
                        "recommendation": "Recommendation 2"
                        },
                        {
                        "name": "Testability",
                        "score": 74,
                        "finding": "Finding 3",
                        "recommendation": "Recommendation 3"
                        },
                        {
                        "name": "Consistency",
                        "score": 76,
                        "finding": "Finding 4",
                        "recommendation": "Recommendation 4"
                        },
                        {
                        "name": "Atomicity",
                        "score": 78,
                        "finding": "Finding 5",
                        "recommendation": "Recommendation 5"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                QualityAnalysisResponse result = provider.analyzeQuality(
                        new QualityAnalysisRequest(
                                UUID.randomUUID(),
                                "The system shall process user requests."
                        )
                );

                assertEquals(
                        List.of(
                                "Clarity",
                                "Specificity",
                                "Testability",
                                "Consistency",
                                "Atomicity"
                        ),
                        result.dimensions().stream()
                                .map(QualityDimension::name)
                                .toList()
                );

                assertEquals(
                        List.of(70, 72, 74, 76, 78),
                        result.dimensions().stream()
                                .map(QualityDimension::score)
                                .toList()
                );
        }

        @Test
        void analyzeQualityRejectsMissingOverallScore() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "dimensions": [],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("overallScore"));
        }

        @Test
        void analyzeQualityRejectsInvalidOverallScore() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": "high",
                        "dimensions": [],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("overallScore"));
        }

        @Test
        void analyzeQualityRejectsOverallScoreBelowZero() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": -1,
                        "dimensions": [],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("out of range"));
        }

        @Test
        void analyzeQualityRejectsOverallScoreAbove100() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 101,
                        "dimensions": [],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("out of range"));
        }

        @Test
        void analyzeQualityRejectsMissingDimensions() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("dimensions"));
        }

        @Test
        void analyzeQualityRejectsInvalidDimensionObject() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "dimensions": [
                        "Clarity"
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("invalid dimension"));
        }

        @Test
        void analyzeQualityRejectsMissingDimensionName() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "dimensions": [
                        {
                        "score": 80,
                        "finding": "Finding",
                        "recommendation": "Recommendation"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("name"));
        }

        @Test
        void analyzeQualityRejectsMissingDimensionScore() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "dimensions": [
                        {
                        "name": "Clarity",
                        "finding": "Finding",
                        "recommendation": "Recommendation"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("score"));
        }

        @Test
        void analyzeQualityRejectsDimensionScoreOutOfRange() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "dimensions": [
                        {
                        "name": "Clarity",
                        "score": 101,
                        "finding": "Finding",
                        "recommendation": "Recommendation"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("out of range"));
        }

        @Test
        void analyzeQualityRejectsMissingFinding() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "dimensions": [
                        {
                        "name": "Clarity",
                        "score": 80,
                        "recommendation": "Recommendation"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("finding"));
        }

        @Test
        void analyzeQualityRejectsMissingRecommendation() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "dimensions": [
                        {
                        "name": "Clarity",
                        "score": 80,
                        "finding": "Finding"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("recommendation"));
        }

        @Test
        void analyzeQualityRejectsUnsupportedDimension() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "dimensions": [
                        {
                        "name": "Readability",
                        "score": 80,
                        "finding": "Finding",
                        "recommendation": "Recommendation"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("unsupported quality dimension"));
        }

        @Test
        void analyzeQualityRejectsDuplicateDimension() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "dimensions": [
                        {
                        "name": "Clarity",
                        "score": 80,
                        "finding": "Finding 1",
                        "recommendation": "Recommendation 1"
                        },
                        {
                        "name": "Clarity",
                        "score": 70,
                        "finding": "Finding 2",
                        "recommendation": "Recommendation 2"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(exception.getMessage().contains("duplicate quality dimension"));
        }

        @Test
        void analyzeQualityRejectsMissingRequiredDimension() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "overallScore": 80,
                        "dimensions": [
                        {
                        "name": "Clarity",
                        "score": 80,
                        "finding": "Finding",
                        "recommendation": "Recommendation"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.analyzeQuality(
                                new QualityAnalysisRequest(
                                        UUID.randomUUID(),
                                        "Some requirement."
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain exactly the required"
                        )
                );
        }


        @Test
        void detectDuplicatesReturnsValidDuplicate() {
                UUID targetId = UUID.randomUUID();
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": 0.88,
                        "relationship": "SAME_BEHAVIOR",
                        "reason": "Both requirements describe the same login behavior."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                DuplicateDetectionResponse result = provider.detectDuplicates(
                        new DuplicateDetectionRequest(
                                targetId,
                                "The system shall allow users to log in.",
                                List.of(
                                        new RequirementCandidate(
                                                candidateId,
                                                "The system shall allow registered users to authenticate."
                                        )
                                )
                        )
                );

                assertEquals(1, result.duplicates().size());

                DuplicateCandidate duplicate = result.duplicates().get(0);

                assertEquals(candidateId, duplicate.requirementId());
                assertEquals(new BigDecimal("0.88"), duplicate.similarity());
                assertEquals("SAME_BEHAVIOR", duplicate.relationship());
                assertEquals(
                        "Both requirements describe the same login behavior.",
                        duplicate.reason()
                );
                assertEquals(
                        0,
                        new BigDecimal("0.90").compareTo(result.confidence())
                );

                verify(client).chatCompletion(any(NvidiaChatRequest.class));
        }

        @Test
        void detectDuplicatesReturnsEmptyWhenNoDuplicatesFound() {
                UUID targetId = UUID.randomUUID();
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [],
                        "confidence": 0.93
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                DuplicateDetectionResponse result = provider.detectDuplicates(
                        new DuplicateDetectionRequest(
                                targetId,
                                "The system shall generate monthly reports.",
                                List.of(
                                        new RequirementCandidate(
                                                candidateId,
                                                "The system shall allow users to change their password."
                                        )
                                )
                        )
                );

                assertTrue(result.duplicates().isEmpty());
                assertEquals(new BigDecimal("0.93"), result.confidence());
        }

        @Test
        void detectDuplicatesReturnsMultipleDuplicates() {
                UUID targetId = UUID.randomUUID();
                UUID candidateId1 = UUID.randomUUID();
                UUID candidateId2 = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": 0.91,
                        "relationship": "SAME_BEHAVIOR",
                        "reason": "Both describe user authentication."
                        },
                        {
                        "requirementId": "%s",
                        "similarity": 0.84,
                        "relationship": "SAME_BEHAVIOR",
                        "reason": "Both describe authentication failure handling."
                        }
                        ],
                        "confidence": 0.89
                        }
                        """.formatted(candidateId1, candidateId2)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                DuplicateDetectionResponse result = provider.detectDuplicates(
                        new DuplicateDetectionRequest(
                                targetId,
                                "The system shall authenticate users and handle failed login attempts.",
                                List.of(
                                        new RequirementCandidate(candidateId1, "Users shall be authenticated."),
                                        new RequirementCandidate(candidateId2, "The system shall handle failed login attempts.")
                                )
                        )
                );

                assertEquals(2, result.duplicates().size());
                assertEquals(candidateId1, result.duplicates().get(0).requirementId());
                assertEquals(candidateId2, result.duplicates().get(1).requirementId());
                assertEquals(new BigDecimal("0.89"), result.confidence());
        }

        @Test
        void detectDuplicatesAllowsEmptyCandidateList() {
                UUID targetId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [],
                        "confidence": 0.99
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                DuplicateDetectionResponse result = provider.detectDuplicates(
                        new DuplicateDetectionRequest(
                                targetId,
                                "The system shall generate reports.",
                                List.of()
                        )
                );

                assertTrue(result.duplicates().isEmpty());
                assertEquals(new BigDecimal("0.99"), result.confidence());
        }

        @Test
        void detectDuplicatesRejectsMissingDuplicatesArray() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a valid duplicates array"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsInvalidDuplicateObject() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": ["invalid"],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "contained an invalid duplicate"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsMissingRequirementId() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "similarity": 0.88,
                        "relationship": "SIMILAR_FUNCTIONALITY",
                        "reason": "Same behavior."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a requirementId"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsInvalidRequirementId() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "not-a-uuid",
                        "similarity": 0.88,
                        "relationship": "SIMILAR_FUNCTIONALITY",
                        "reason": "Same behavior."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "invalid requirementId"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsUnknownCandidateId() {
                UUID unknownId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": 0.88,
                        "relationship": "SIMILAR_FUNCTIONALITY",
                        "reason": "Same behavior."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(unknownId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                UUID suppliedCandidateId = UUID.randomUUID();

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        suppliedCandidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "was not supplied as a candidate"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsDuplicateRequirementId() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": 0.88,
                        "relationship": "SAME_BEHAVIOR",
                        "reason": "Same behavior."
                        },
                        {
                        "requirementId": "%s",
                        "similarity": 0.85,
                        "relationship": "SAME_BEHAVIOR",
                        "reason": "Same behavior again."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId, candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "returned duplicate requirementId"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsMissingSimilarity() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "relationship": "SIMILAR_FUNCTIONALITY",
                        "reason": "Same behavior."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a similarity value"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsSimilarityBelowZero() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": -0.1,
                        "relationship": "SIMILAR_FUNCTIONALITY",
                        "reason": "Same behavior."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "out of range"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsSimilarityAboveOne() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": 1.1,
                        "relationship": "SIMILAR_FUNCTIONALITY",
                        "reason": "Same behavior."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "out of range"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsMissingRelationship() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": 0.88,
                        "reason": "Same behavior."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a relationship"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsMissingReason() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": 0.88,
                        "relationship": "SIMILAR_FUNCTIONALITY"
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a reason"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsMissingConfidence() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": 0.88,
                        "relationship": "SAME_BEHAVIOR",
                        "reason": "Same behavior."
                        }
                        ]
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "confidence"
                        )
                );
        }

        @Test
        void detectDuplicatesRejectsConfidenceOutOfRange() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [],
                        "confidence": 1.1
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "confidence"
                        )
                );
        }

        @Test
        void detectDuplicatesRecognizesSemanticallyEquivalentDockerRequirements() {
                UUID targetId = UUID.fromString(
                        "a1ca132f-5fe7-4b91-8f53-dd1ad87e166e"
                );

                UUID candidateId = UUID.fromString(
                        "1027c5c8-16c7-47de-a64e-7a0123d1bc6d"
                );

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "duplicates": [
                                {
                                "requirementId": "%s",
                                "similarity": 1.0,
                                "relationship": "SAME_BEHAVIOR",
                                "reason": "Both requirements describe creating a Docker image for the frontend application."
                                }
                        ],
                        "confidence": 0.96
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                DuplicateDetectionResponse result = provider.detectDuplicates(
                        new DuplicateDetectionRequest(
                                targetId,
                                "The system shall allow users to build a Docker image for the frontend application.",
                                List.of(
                                        new RequirementCandidate(
                                                candidateId,
                                                "The system shall allow users to create a Docker image for the frontend application."
                                        )
                                )
                        )
                );

                assertEquals(1, result.duplicates().size());

                DuplicateCandidate duplicate = result.duplicates().get(0);

                assertEquals(candidateId, duplicate.requirementId());

                assertEquals(
                        0,
                        new BigDecimal("1.0")
                                .compareTo(duplicate.similarity())
                );

                assertEquals(
                        "SAME_BEHAVIOR",
                        duplicate.relationship()
                );

                assertEquals(
                        "Both requirements describe creating a Docker image for the frontend application.",
                        duplicate.reason()
                );

                assertEquals(
                        0,
                        new BigDecimal("0.96")
                                .compareTo(result.confidence())
                );

                verify(client).chatCompletion(any(NvidiaChatRequest.class));
        }

        @Test
        void detectDuplicatesRejectsInvalidJson() {
                NvidiaChatResponse response = chatResponse(
                        "not valid json at all"
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectDuplicates(
                                new DuplicateDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "not valid JSON"
                        )
                );
        }

        @Test
        void detectDuplicatesHandlesMarkdownCodeFence() {
        UUID candidateId = UUID.randomUUID();

        NvidiaChatResponse response = chatResponse(
                """
                ```json
                {
                "duplicates": [
                        {
                        "requirementId": "%s",
                        "similarity": 0.88,
                        "relationship": "SAME_BEHAVIOR",
                        "reason": "Same behavior."
                        }
                ],
                "confidence": 0.90
                }
                ```
                """.formatted(candidateId)
        );

        when(client.chatCompletion(any())).thenReturn(response);

        DuplicateDetectionResponse result = provider.detectDuplicates(
                new DuplicateDetectionRequest(
                        UUID.randomUUID(),
                        "Some requirement.",
                        List.of(
                                new RequirementCandidate(
                                        candidateId,
                                        "Candidate requirement."
                                )
                        )
                )
        );

        assertEquals(1, result.duplicates().size());
        assertEquals(candidateId, result.duplicates().get(0).requirementId());
        assertEquals(
                0,
                new BigDecimal("0.88")
                        .compareTo(result.duplicates().get(0).similarity())
        );
        }

        @Test
        void detectConflictsReturnsValidConflict() {
                UUID targetId = UUID.randomUUID();
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "severity": "HIGH",
                        "reason": "The target permits an action that the candidate prohibits.",
                        "suggestion": "Clarify which rule takes precedence."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                ConflictDetectionResponse result = provider.detectConflicts(
                        new ConflictDetectionRequest(
                                targetId,
                                "The system shall allow users to export reports.",
                                List.of(
                                        new RequirementCandidate(
                                                candidateId,
                                                "The system shall prohibit users from exporting reports."
                                        )
                                )
                        )
                );

                assertEquals(1, result.conflicts().size());

                ConflictFinding conflict = result.conflicts().get(0);

                assertEquals(candidateId, conflict.requirementId());
                assertEquals("LOGICAL_CONTRADICTION", conflict.conflictType());
                assertEquals("HIGH", conflict.severity());
                assertEquals(
                        "The target permits an action that the candidate prohibits.",
                        conflict.reason()
                );
                assertEquals(
                        "Clarify which rule takes precedence.",
                        conflict.suggestion()
                );
                assertEquals(
                        0,
                        new BigDecimal("0.90").compareTo(result.confidence())
                );

                verify(client).chatCompletion(any(NvidiaChatRequest.class));
        }

        @Test
        void detectConflictsReturnsEmptyWhenNoConflictsFound() {
                UUID targetId = UUID.randomUUID();
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [],
                        "confidence": 0.93
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                ConflictDetectionResponse result = provider.detectConflicts(
                        new ConflictDetectionRequest(
                                targetId,
                                "The system shall generate monthly reports.",
                                List.of(
                                        new RequirementCandidate(
                                                candidateId,
                                                "The system shall allow users to change their password."
                                        )
                                )
                        )
                );

                assertTrue(result.conflicts().isEmpty());
                assertEquals(
                        0,
                        new BigDecimal("0.93").compareTo(result.confidence())
                );
        }

        @Test
        void detectConflictsReturnsMultipleConflicts() {
                UUID targetId = UUID.randomUUID();
                UUID candidateId1 = UUID.randomUUID();
                UUID candidateId2 = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "conflictType": "INCOMPATIBLE_BEHAVIOR",
                        "severity": "HIGH",
                        "reason": "The requirements specify opposite authentication behavior.",
                        "suggestion": "Define one authentication rule."
                        },
                        {
                        "requirementId": "%s",
                        "conflictType": "CONSTRAINT_CONFLICT",
                        "severity": "MEDIUM",
                        "reason": "The requirements specify incompatible limits.",
                        "suggestion": "Establish a single consistent limit."
                        }
                        ],
                        "confidence": 0.89
                        }
                        """.formatted(candidateId1, candidateId2)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                ConflictDetectionResponse result = provider.detectConflicts(
                        new ConflictDetectionRequest(
                                targetId,
                                "The system shall authenticate users with the specified policy.",
                                List.of(
                                        new RequirementCandidate(
                                                candidateId1,
                                                "The system shall use an incompatible authentication policy."
                                        ),
                                        new RequirementCandidate(
                                                candidateId2,
                                                "The system shall enforce a different limit."
                                        )
                                )
                        )
                );

                assertEquals(2, result.conflicts().size());
                assertEquals(
                        candidateId1,
                        result.conflicts().get(0).requirementId()
                );
                assertEquals(
                        candidateId2,
                        result.conflicts().get(1).requirementId()
                );
                assertEquals(
                        0,
                        new BigDecimal("0.89").compareTo(result.confidence())
                );
        }

        @Test
        void detectConflictsAllowsEmptyCandidateList() {
                UUID targetId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [],
                        "confidence": 0.99
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                ConflictDetectionResponse result = provider.detectConflicts(
                        new ConflictDetectionRequest(
                                targetId,
                                "The system shall generate reports.",
                                List.of()
                        )
                );

                assertTrue(result.conflicts().isEmpty());
                assertEquals(
                        0,
                        new BigDecimal("0.99").compareTo(result.confidence())
                );
        }

        @Test
        void detectConflictsRejectsMissingConflictsArray() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a valid conflicts array"
                        )
                );
        }

        @Test
        void detectConflictsRejectsInvalidConflictObject() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": ["invalid"],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "contained an invalid conflict"
                        )
                );
        }

        @Test
        void detectConflictsRejectsMissingRequirementId() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "severity": "HIGH",
                        "reason": "Conflicting behavior.",
                        "suggestion": "Clarify the rule."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a requirementId"
                        )
                );
        }

        @Test
        void detectConflictsRejectsInvalidRequirementId() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "not-a-uuid",
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "severity": "HIGH",
                        "reason": "Conflicting behavior.",
                        "suggestion": "Clarify the rule."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "invalid requirementId"
                        )
                );
        }

        @Test
        void detectConflictsRejectsUnknownCandidateId() {
                UUID unknownId = UUID.randomUUID();
                UUID suppliedCandidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "severity": "HIGH",
                        "reason": "Conflicting behavior.",
                        "suggestion": "Clarify the rule."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(unknownId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        suppliedCandidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "was not supplied as a candidate"
                        )
                );
        }

        @Test
        void detectConflictsRejectsDuplicateRequirementId() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "severity": "HIGH",
                        "reason": "Conflicting behavior.",
                        "suggestion": "Clarify the rule."
                        },
                        {
                        "requirementId": "%s",
                        "conflictType": "INCOMPATIBLE_BEHAVIOR",
                        "severity": "MEDIUM",
                        "reason": "Same candidate returned again.",
                        "suggestion": "Review the conflict."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId, candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "returned duplicate requirementId"
                        )
                );
        }

        @Test
        void detectConflictsRejectsMissingConflictType() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "severity": "HIGH",
                        "reason": "Conflicting behavior.",
                        "suggestion": "Clarify the rule."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a conflictType"
                        )
                );
        }

        @Test
        void detectConflictsRejectsMissingSeverity() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "reason": "Conflicting behavior.",
                        "suggestion": "Clarify the rule."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a severity"
                        )
                );
        }

        @Test
        void detectConflictsRejectsMissingReason() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "severity": "HIGH",
                        "suggestion": "Clarify the rule."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a reason"
                        )
                );
        }

        @Test
        void detectConflictsRejectsMissingSuggestion() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "severity": "HIGH",
                        "reason": "Conflicting behavior."
                        }
                        ],
                        "confidence": 0.90
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "did not contain a suggestion"
                        )
                );
        }

        @Test
        void detectConflictsRejectsMissingConfidence() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "severity": "HIGH",
                        "reason": "Conflicting behavior.",
                        "suggestion": "Clarify the rule."
                        }
                        ]
                        }
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of(
                                                new RequirementCandidate(
                                                        candidateId,
                                                        "Candidate requirement."
                                                )
                                        )
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "confidence"
                        )
                );
        }

        @Test
        void detectConflictsRejectsConfidenceOutOfRange() {
                NvidiaChatResponse response = chatResponse(
                        """
                        {
                        "conflicts": [],
                        "confidence": 1.1
                        }
                        """
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "confidence"
                        )
                );
        }

        @Test
        void detectConflictsRejectsInvalidJson() {
                NvidiaChatResponse response = chatResponse(
                        "not valid json at all"
                );

                when(client.chatCompletion(any())).thenReturn(response);

                IllegalStateException exception = assertThrows(
                        IllegalStateException.class,
                        () -> provider.detectConflicts(
                                new ConflictDetectionRequest(
                                        UUID.randomUUID(),
                                        "Some requirement.",
                                        List.of()
                                )
                        )
                );

                assertTrue(
                        exception.getMessage().contains(
                                "not valid JSON"
                        )
                );
        }

        @Test
        void detectConflictsHandlesMarkdownCodeFence() {
                UUID candidateId = UUID.randomUUID();

                NvidiaChatResponse response = chatResponse(
                        """
                        ```json
                        {
                        "conflicts": [
                        {
                        "requirementId": "%s",
                        "conflictType": "LOGICAL_CONTRADICTION",
                        "severity": "HIGH",
                        "reason": "Conflicting behavior.",
                        "suggestion": "Clarify the rule."
                        }
                        ],
                        "confidence": 0.90
                        }
                        ```
                        """.formatted(candidateId)
                );

                when(client.chatCompletion(any())).thenReturn(response);

                ConflictDetectionResponse result = provider.detectConflicts(
                        new ConflictDetectionRequest(
                                UUID.randomUUID(),
                                "Some requirement.",
                                List.of(
                                        new RequirementCandidate(
                                                candidateId,
                                                "Candidate requirement."
                                        )
                                )
                        )
                );

                assertEquals(1, result.conflicts().size());
                assertEquals(
                        candidateId,
                        result.conflicts().get(0).requirementId()
                );
                assertEquals(
                        "LOGICAL_CONTRADICTION",
                        result.conflicts().get(0).conflictType()
                );
                assertEquals(
                        "HIGH",
                        result.conflicts().get(0).severity()
                );
        }


    private NvidiaChatResponse chatResponse(String content) {
        return new NvidiaChatResponse(
                List.of(new NvidiaChatResponse.Choice(
                        new NvidiaChatResponse.Message("assistant", content)
                ))
        );
    }
}
