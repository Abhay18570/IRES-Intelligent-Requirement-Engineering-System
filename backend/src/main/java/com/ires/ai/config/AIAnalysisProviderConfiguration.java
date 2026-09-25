package com.ires.ai.config;

import com.ires.ai.provider.jev.JevAIAnalysisProvider;
import com.ires.ai.provider.jev.JevApiClient;
import com.ires.ai.provider.nvidia.NvidiaAIAnalysisProvider;
import com.ires.ai.provider.nvidia.NvidiaApiClient;
import com.ires.ai.service.AIAnalysisProvider;
import com.ires.ai.service.MockAIAnalysisProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class AIAnalysisProviderConfiguration {

    @Bean
    public MockAIAnalysisProvider mockAIAnalysisProvider(
            @Value("${app.ai.mock-enabled:true}") boolean available
    ) {
        return new MockAIAnalysisProvider(available);
    }

    @Bean
    public JevApiClient jevApiClient(
            RestClient.Builder restClientBuilder,
            AIAnalysisProperties properties
    ) {
        return new JevApiClient(
                restClientBuilder,
                properties
        );
    }

    @Bean
    public JevAIAnalysisProvider jevAIAnalysisProvider(
            JevApiClient client,
            AIAnalysisProperties properties
    ) {
        return new JevAIAnalysisProvider(
                client,
                properties.getModel()
        );
    }

    @Bean
    public NvidiaApiClient nvidiaApiClient(
            RestClient.Builder restClientBuilder,
            AIAnalysisProperties properties
    ) {
        return new NvidiaApiClient(
                restClientBuilder,
                properties
        );
    }

    @Bean
    public NvidiaAIAnalysisProvider nvidiaAIAnalysisProvider(
            NvidiaApiClient client,
            AIAnalysisProperties properties
    ) {
        return new NvidiaAIAnalysisProvider(
                client,
                properties.getModel()
        );
    }

    @Bean
    @Primary
    public AIAnalysisProvider aiAnalysisProvider(
            AIAnalysisProperties properties,
            MockAIAnalysisProvider mockAIAnalysisProvider,
            JevAIAnalysisProvider jevAIAnalysisProvider,
            NvidiaAIAnalysisProvider nvidiaAIAnalysisProvider
    ) {
        String provider = properties.getProvider();

        if ("mock".equalsIgnoreCase(provider)) {
            return mockAIAnalysisProvider;
        }

        if ("jev".equalsIgnoreCase(provider)) {
        throw new IllegalArgumentException(
                "Jev AI analysis provider is not available for all required capabilities."
        );
}

        if ("nvidia".equalsIgnoreCase(provider)) {
            return nvidiaAIAnalysisProvider;
        }

        throw new IllegalArgumentException(
                "Unsupported AI analysis provider: " + provider
        );
    }
}