package com.ires.ai.provider.nvidia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ires.ai.config.AIAnalysisProperties;
import com.ires.ai.provider.nvidia.dto.NvidiaChatMessage;
import com.ires.ai.provider.nvidia.dto.NvidiaChatRequest;
import com.ires.ai.provider.nvidia.dto.NvidiaChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NvidiaApiClientTest {

    private static final String BASE_URL = "http://nvidia-test.local";

    private ObjectMapper objectMapper;
    private AIAnalysisProperties properties;
    private MockRestServiceServer server;
    private NvidiaApiClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        properties = new AIAnalysisProperties();
        properties.setBaseUrl(BASE_URL);
        properties.setApiKey("test-nvidia-api-key");
        properties.setTimeoutMs(5000L);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);

        server = MockRestServiceServer
                .bindTo(builder)
                .build();

        client = new NvidiaApiClient(
                builder.build(),
                properties);
    }

    @Test
    void chatCompletionSendsCorrectHttpRequestAndMapsResponse() {
        String responseBody = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\\"classification\\": \\"FUNCTIONAL\\", \\"confidence\\": 0.94, \\"reason\\": \\"Describes system behavior.\\"}"
                      }
                    }
                  ]
                }
                """;

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer test-nvidia-api-key"
                ))
                .andExpect(header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                ))
                .andExpect(content().json("""
                        {
                          "model": "meta/llama-3.1-70b-instruct",
                          "messages": [
                            {"role": "system", "content": "You are helpful."},
                            {"role": "user", "content": "Classify this."}
                          ]
                        }
                        """, false))
                .andRespond(withSuccess(
                        responseBody,
                        MediaType.APPLICATION_JSON
                ));

        NvidiaChatRequest request = new NvidiaChatRequest(
                "meta/llama-3.1-70b-instruct",
                List.of(
                        new NvidiaChatMessage("system", "You are helpful."),
                        new NvidiaChatMessage("user", "Classify this.")
                )
        );

        NvidiaChatResponse response = client.chatCompletion(request);

        assertNotNull(response);
        assertNotNull(response.choices());
        assertEquals(1, response.choices().size());

        NvidiaChatResponse.Message message =
                response.choices().get(0).message();

        assertEquals("assistant", message.role());
        assertTrue(message.content().contains("FUNCTIONAL"));

        server.verify();
    }

    @Test
    void chatCompletionRejectsMissingApiKey() {
        properties.setApiKey("");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);

        NvidiaApiClient clientWithoutKey =
                new NvidiaApiClient(builder.build(), properties);

        NvidiaChatRequest request = new NvidiaChatRequest(
                "meta/llama-3.1-70b-instruct",
                List.of(
                        new NvidiaChatMessage("user", "Classify this.")
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> clientWithoutKey.chatCompletion(request)
        );

        assertEquals(
                "NVIDIA API key is not configured.",
                exception.getMessage()
        );
    }

    @Test
    void chatCompletionRejectsNullApiKey() {
        properties.setApiKey(null);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL);

        NvidiaApiClient clientWithNullKey =
                new NvidiaApiClient(builder.build(), properties);

        NvidiaChatRequest request = new NvidiaChatRequest(
                "meta/llama-3.1-70b-instruct",
                List.of(
                        new NvidiaChatMessage("user", "Classify this.")
                )
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> clientWithNullKey.chatCompletion(request)
        );

        assertEquals(
                "NVIDIA API key is not configured.",
                exception.getMessage()
        );
    }

    @Test
    void chatCompletionMapsEmptyChoices() {
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": []
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        NvidiaChatRequest request = new NvidiaChatRequest(
                "meta/llama-3.1-70b-instruct",
                List.of(
                        new NvidiaChatMessage("user", "Classify this.")
                )
        );

        NvidiaChatResponse response = client.chatCompletion(request);

        assertNotNull(response);
        assertNotNull(response.choices());
        assertTrue(response.choices().isEmpty());

        server.verify();
    }
}
