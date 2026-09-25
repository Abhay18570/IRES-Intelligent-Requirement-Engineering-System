package com.ires.ai.provider.jev;

import com.ires.ai.config.AIAnalysisProperties;
import com.ires.ai.provider.jev.dto.JevDecisionRequest;
import com.ires.ai.provider.jev.dto.JevDecisionResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

public class JevApiClient {

    private static final String DEFAULT_BASE_URL = "https://www.jevai.org";
    private static final String DECISIONS_PATH = "/api/v1/decisions";

    private final RestClient restClient;
    private final AIAnalysisProperties properties;

    public JevApiClient(
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
    public JevApiClient(
            RestClient restClient,
            AIAnalysisProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public JevDecisionResponse decide(JevDecisionRequest request) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "Jev API key is not configured."
            );
        }

        return restClient
                .post()
                .uri(DECISIONS_PATH)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + properties.getApiKey()
                )
                .body(request)
                .retrieve()
                .body(JevDecisionResponse.class);
    }
}
