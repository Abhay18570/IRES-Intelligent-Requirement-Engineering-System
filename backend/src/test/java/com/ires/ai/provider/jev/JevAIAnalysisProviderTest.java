package com.ires.ai.provider.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.ai.dto.analysis.AmbiguityFinding;
import com.ires.ai.dto.analysis.AmbiguityRequest;
import com.ires.ai.dto.analysis.AmbiguityResponse;
import com.ires.ai.dto.analysis.ClassificationRequest;
import com.ires.ai.dto.analysis.ClassificationResponse;
import com.ires.ai.provider.jev.dto.JevDecisionRequest;
import com.ires.ai.provider.jev.dto.JevDecisionResponse;
import org.junit.jupiter.api.Test;


import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JevAIAnalysisProviderTest {

    private static final UUID REQUIREMENT_ID = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void classifyMapsJevResponseToIresResponse() throws Exception {
        JevApiClient client = mock(JevApiClient.class);

        UUID requirementId = UUID.randomUUID();

        JsonNode data = objectMapper.readTree("""
                {
                  "answers": {
                    "classification": {
                      "choice": "FUNCTIONAL",
                      "confidence": 0.94
                    }
                  }
                }
                """);

        when(client.decide(any(JevDecisionRequest.class)))
                .thenReturn(new JevDecisionResponse(
                        0,
                        "Decision completed",
                        data
                ));

        JevAIAnalysisProvider provider =
                new JevAIAnalysisProvider(client, "typesafe-ai/jev");

        ClassificationResponse response = provider.classify(
                new ClassificationRequest(
                        requirementId,
                        "The system shall allow users to reset their password."
                )
        );

        assertEquals("FUNCTIONAL", response.classification());
        assertEquals(
                new BigDecimal("0.94"),
                response.confidence()
        );
        assertTrue(response.reason().contains("FUNCTIONAL"));

        verify(client).decide(any(JevDecisionRequest.class));
    }

    @Test
    void classifyUsesDefaultModelWhenConfiguredModelIsBlank() throws Exception {
        JevApiClient client = mock(JevApiClient.class);

        JsonNode data = objectMapper.readTree("""
                {
                  "answers": {
                    "classification": {
                      "choice": "BUSINESS",
                      "confidence": 0.88
                    }
                  }
                }
                """);

        when(client.decide(any(JevDecisionRequest.class)))
                .thenReturn(new JevDecisionResponse(
                        0,
                        "Decision completed",
                        data
                ));

        JevAIAnalysisProvider provider =
                new JevAIAnalysisProvider(client, "");

        provider.classify(
                new ClassificationRequest(
                        UUID.randomUUID(),
                        "The system shall support the organization's business approval process."
                )
        );

        verify(client).decide(argThat(request ->
                "typesafe-ai/jev".equals(request.model())
        ));
    }

    @Test
    void classifyPassesConfiguredModel() throws Exception {
        JevApiClient client = mock(JevApiClient.class);

        JsonNode data = objectMapper.readTree("""
                {
                  "answers": {
                    "classification": {
                      "choice": "TECHNICAL",
                      "confidence": 0.91
                    }
                  }
                }
                """);

        when(client.decide(any(JevDecisionRequest.class)))
                .thenReturn(new JevDecisionResponse(
                        0,
                        "Decision completed",
                        data
                ));

        JevAIAnalysisProvider provider =
                new JevAIAnalysisProvider(client, "custom-jev-model");

        provider.classify(
                new ClassificationRequest(
                        UUID.randomUUID(),
                        "The service shall use PostgreSQL for persistent storage."
                )
        );

        verify(client).decide(argThat(request ->
                "custom-jev-model".equals(request.model())
        ));
    }

    @Test
    void classifyRejectsFailedJevResponse() {
        JevApiClient client = mock(JevApiClient.class);

        when(client.decide(any(JevDecisionRequest.class)))
                .thenReturn(new JevDecisionResponse(
                        1001,
                        "Invalid decision request",
                        null
                ));

        JevAIAnalysisProvider provider =
                new JevAIAnalysisProvider(client, "typesafe-ai/jev");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(
                        new ClassificationRequest(
                                UUID.randomUUID(),
                                "The system shall allow login."
                        )
                )
        );

        assertTrue(
                exception.getMessage().contains("Jev decision failed")
        );
    }

    @Test
    void classifyRejectsNullJevResponse() {
        JevApiClient client = mock(JevApiClient.class);

        when(client.decide(any(JevDecisionRequest.class)))
                .thenReturn(null);

        JevAIAnalysisProvider provider =
                new JevAIAnalysisProvider(client, "typesafe-ai/jev");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(
                        new ClassificationRequest(
                                UUID.randomUUID(),
                                "The system shall allow login."
                        )
                )
        );

        assertEquals(
                "Jev returned an empty response.",
                exception.getMessage()
        );
    }

    @Test
    void classifyRejectsResponseWithoutClassification() throws Exception {
        JevApiClient client = mock(JevApiClient.class);

        JsonNode data = objectMapper.readTree("""
                {
                  "answers": {
                    "classification": {}
                  }
                }
                """);

        when(client.decide(any(JevDecisionRequest.class)))
                .thenReturn(new JevDecisionResponse(
                        0,
                        "Decision completed",
                        data
                ));

        JevAIAnalysisProvider provider =
                new JevAIAnalysisProvider(client, "typesafe-ai/jev");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(
                        new ClassificationRequest(
                                UUID.randomUUID(),
                                "The system shall allow login."
                        )
                )
        );

        assertEquals(
                "Jev response did not contain a classification choice.",
                exception.getMessage()
        );
    }

    @Test
    void classifyUsesZeroConfidenceWhenJevOmitsConfidence() throws Exception {
        JevApiClient client = mock(JevApiClient.class);

        JsonNode data = objectMapper.readTree("""
                {
                  "answers": {
                    "classification": {
                      "choice": "NON_FUNCTIONAL"
                    }
                  }
                }
                """);

        when(client.decide(any(JevDecisionRequest.class)))
                .thenReturn(new JevDecisionResponse(
                        0,
                        "Decision completed",
                        data
                ));

        JevAIAnalysisProvider provider =
                new JevAIAnalysisProvider(client, "typesafe-ai/jev");

        ClassificationResponse response = provider.classify(
                new ClassificationRequest(
                        UUID.randomUUID(),
                        "The system shall respond within two seconds."
                )
        );

        assertEquals("NON_FUNCTIONAL", response.classification());
        assertEquals(BigDecimal.ZERO, response.confidence());
    }

    @Test
    void classifyBuildsExpectedJevRequest() throws Exception {
        JevApiClient client = mock(JevApiClient.class);

        JsonNode data = objectMapper.readTree("""
                {
                  "answers": {
                    "classification": {
                      "choice": "FUNCTIONAL",
                      "confidence": 0.95
                    }
                  }
                }
                """);

        when(client.decide(any(JevDecisionRequest.class)))
                .thenReturn(new JevDecisionResponse(
                        0,
                        "Decision completed",
                        data
                ));

        UUID requirementId = UUID.randomUUID();
        String requirementText = "The system shall allow users to upload documents.";

        JevAIAnalysisProvider provider =
                new JevAIAnalysisProvider(client, "typesafe-ai/jev");

        provider.classify(
                new ClassificationRequest(
                        requirementId,
                        requirementText
                )
        );

        verify(client).decide(argThat(request -> {
            if (!(request.state() instanceof Map<?, ?> state)) {
                return false;
            }

            if (!(request.questions().containsKey("classification"))) {
                return false;
            }

            Object stateRequirementId = state.get("requirement_id");
            Object stateRequirementText = state.get("requirement_text");

            return requirementId.equals(stateRequirementId)
                    && requirementText.equals(stateRequirementText)
                    && "choice".equals(
                            request.questions()
                                    .get("classification")
                                    .type()
                    );
        }));
    }

    @Test
    void detectAmbiguityIsExplicitlyUnsupported() {
        JevApiClient client = mock(JevApiClient.class);
        JevAIAnalysisProvider provider = new JevAIAnalysisProvider(client, "typesafe-ai/jev");

        AmbiguityRequest request = new AmbiguityRequest(
                REQUIREMENT_ID,
                "The system shall respond quickly."
        );

        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> provider.detectAmbiguity(request)
        );

        assertEquals(
                "Jev ambiguity detection is not implemented yet.",
                exception.getMessage()
        );
    }


    @Test
void classifyRejectsUnsupportedClassification() throws Exception {
    JevApiClient client = mock(JevApiClient.class);

    JsonNode data = objectMapper.readTree("""
            {
              "answers": {
                "classification": {
                  "choice": "INVALID_TYPE",
                  "confidence": 0.90
                }
              }
            }
            """);

    when(client.decide(any(JevDecisionRequest.class)))
            .thenReturn(new JevDecisionResponse(
                    0,
                    "Decision completed",
                    data
            ));

    JevAIAnalysisProvider provider =
            new JevAIAnalysisProvider(client, "typesafe-ai/jev");

    IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> provider.classify(
                    new ClassificationRequest(
                            UUID.randomUUID(),
                            "The system shall allow users to log in."
                    )
            )
    );

    assertEquals(
            "Jev response contained an unsupported classification: INVALID_TYPE",
            exception.getMessage()
    );
}

        @Test
        void classifyRejectsConfidenceOutsideRange() throws Exception {
        JevApiClient client = mock(JevApiClient.class);

        JsonNode data = objectMapper.readTree("""
                {
                "answers": {
                        "classification": {
                        "choice": "FUNCTIONAL",
                        "confidence": 1.5
                        }
                }
                }
                """);

        when(client.decide(any(JevDecisionRequest.class)))
                .thenReturn(new JevDecisionResponse(
                        0,
                        "Decision completed",
                        data
                ));

        JevAIAnalysisProvider provider =
                new JevAIAnalysisProvider(client, "typesafe-ai/jev");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> provider.classify(
                        new ClassificationRequest(
                                UUID.randomUUID(),
                                "The system shall allow users to log in."
                        )
                )
        );

        assertEquals(
                "Jev response contained an invalid classification confidence: 1.5",
                exception.getMessage()
        );
        }
}
