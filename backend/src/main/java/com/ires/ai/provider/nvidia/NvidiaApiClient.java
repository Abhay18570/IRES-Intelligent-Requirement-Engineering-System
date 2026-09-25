package com.ires.ai.provider.nvidia;

import com.ires.ai.config.AIAnalysisProperties;
import com.ires.ai.provider.nvidia.dto.NvidiaChatRequest;
import com.ires.ai.provider.nvidia.dto.NvidiaChatResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public class NvidiaApiClient {

    private static final String DEFAULT_BASE_URL = "https://integrate.api.nvidia.com/v1";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final RestClient restClient;
    private final AIAnalysisProperties properties;

    public NvidiaApiClient(
            RestClient.Builder restClientBuilder,
            AIAnalysisProperties properties
    ) {
        this.properties = properties;

        String baseUrl = properties.getBaseUrl();

        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        }

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        int timeout = Math.toIntExact(properties.getTimeoutMs());

        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    public NvidiaApiClient(
            RestClient restClient,
            AIAnalysisProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public NvidiaChatResponse chatCompletion(NvidiaChatRequest request) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "NVIDIA API key is not configured."
            );
        }

        return restClient
                .post()
                .uri(CHAT_COMPLETIONS_PATH)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + properties.getApiKey()
                )
                .body(request)
                .retrieve()
                .body(NvidiaChatResponse.class);
    }
}
