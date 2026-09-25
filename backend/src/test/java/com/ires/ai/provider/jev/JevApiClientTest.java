package com.ires.ai.provider.jev;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.ai.config.AIAnalysisProperties;
import com.ires.ai.provider.jev.dto.JevDecisionRequest;
import com.ires.ai.provider.jev.dto.JevDecisionResponse;
import com.ires.ai.provider.jev.dto.JevQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class JevApiClientTest {

    private static final String BASE_URL = "http://jev-test.local";

    private ObjectMapper objectMapper;
    private AIAnalysisProperties properties;
    private MockRestServiceServer server;
    private JevApiClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        properties = new AIAnalysisProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setApiKey("test-jev-api-key");
        properties.setTimeoutMs(5000L);

       RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);

        server = MockRestServiceServer
                .bindTo(builder)
                .build();

        client = new JevApiClient(
                builder.build(),
                properties);
    }

    @Test
    void decideSendsCorrectHttpRequestAndMapsResponse() throws Exception {
        String responseBody = """
                {
                  "code": 0,
                  "message": "Decision completed",
                  "data": {
                    "answers": {
                      "classification": {
                        "choice": "FUNCTIONAL",
                        "confidence": 0.94
                      }
                    }
                  }
                }
                """;

        server.expect(requestTo(BASE_URL + "/api/v1/decisions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer test-jev-api-key"
                ))
                .andExpect(header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(content().json("""
                        {
                          "model": "typesafe-ai/jev",
                          "state": {
                            "requirement_id": "req-123",
                            "requirement_text": "The system shall allow login."
                          },
                          "questions": {
                            "classification": {
                              "type": "choice",
                              "instructions": "Classify the supplied software requirement into exactly one requirement type.",
                              "criteria": {
                                "FUNCTIONAL": "Describes behavior or functionality that the system must provide."
                              }
                            }
                          }
                        }
                        """, false))
                .andRespond(withSuccess(
                        responseBody,
                        MediaType.APPLICATION_JSON
                ));

        JevQuestion question = new JevQuestion(
                "choice",
                "Classify the supplied software requirement into exactly one requirement type.",
                Map.of(
                        "FUNCTIONAL",
                        "Describes behavior or functionality that the system must provide."
                )
        );

        JevDecisionRequest request = new JevDecisionRequest(
                "typesafe-ai/jev",
                Map.of(
                        "requirement_id", "req-123",
                        "requirement_text", "The system shall allow login."
                ),
                Map.of("classification", question)
        );

        JevDecisionResponse response = client.decide(request);

        assertNotNull(response);
        assertEquals(0, response.code());
        assertEquals("Decision completed", response.message());

        JsonNode answer = response.data()
                .path("answers")
                .path("classification");

        assertEquals("FUNCTIONAL", answer.path("choice").asText());
        assertEquals(
                "0.94",
                answer.path("confidence").decimalValue().toString()
        );

        server.verify();
    }

    @Test
    void decideRejectsMissingApiKey() {
        properties.setApiKey("");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);

        JevApiClient clientWithoutKey =
                new JevApiClient(builder.build(), properties);

        JevDecisionRequest request = new JevDecisionRequest(
                "typesafe-ai/jev",
                Map.of(),
                Map.of()
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> clientWithoutKey.decide(request)
        );

        assertEquals(
                "Jev API key is not configured.",
                exception.getMessage()
        );
    }

    @Test
    void decideMapsJevErrorResponse() {
        server.expect(requestTo(BASE_URL + "/api/v1/decisions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                          "code": 1001,
                          "message": "Invalid request",
                          "data": null
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        JevDecisionResponse response = client.decide(
                new JevDecisionRequest(
                        "typesafe-ai/jev",
                        Map.of(),
                        Map.of()
                )
        );

        assertNotNull(response);
        assertEquals(1001, response.code());
        assertEquals("Invalid request", response.message());
        assertTrue(response.data().isNull());

        server.verify();
    }

    @Test
    void decideMapsEmptyData() {
        server.expect(requestTo(BASE_URL + "/api/v1/decisions"))
                .andRespond(withSuccess(
                        """
                        {
                          "code": 0,
                          "message": "Decision completed"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        JevDecisionResponse response = client.decide(
                new JevDecisionRequest(
                        "typesafe-ai/jev",
                        Map.of(),
                        Map.of()
                )
        );

        assertNotNull(response);
        assertEquals(0, response.code());
        assertEquals("Decision completed", response.message());
        assertNull(response.data());

        server.verify();
    }
}
