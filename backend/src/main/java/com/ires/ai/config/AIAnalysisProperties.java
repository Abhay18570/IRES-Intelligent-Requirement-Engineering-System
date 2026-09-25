package com.ires.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.analysis")
public class AIAnalysisProperties {

    private String provider = "mock";
    private String model = "";
    private String baseUrl = "";
    private String apiKey = "";
    private Long timeoutMs = 30000L;

    public AIAnalysisProperties() {
    }

    public AIAnalysisProperties(String provider, String model, String baseUrl, String apiKey, Long timeoutMs) {
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}

