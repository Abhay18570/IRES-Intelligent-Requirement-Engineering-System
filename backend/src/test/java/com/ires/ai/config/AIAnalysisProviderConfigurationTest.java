package com.ires.ai.config;

import com.ires.ai.service.AIAnalysisProvider;
import com.ires.ai.service.MockAIAnalysisProvider;
import com.ires.ai.provider.jev.JevAIAnalysisProvider;
import com.ires.ai.provider.nvidia.NvidiaAIAnalysisProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AIAnalysisProviderConfigurationTest {

    @Test
    void rejectsJevAsGlobalProvider() {
        AIAnalysisProperties properties = new AIAnalysisProperties();
        properties.setProvider("jev");

        AIAnalysisProviderConfiguration configuration =
                new AIAnalysisProviderConfiguration();

        MockAIAnalysisProvider mockProvider =
                mock(MockAIAnalysisProvider.class);
        JevAIAnalysisProvider jevProvider =
                mock(JevAIAnalysisProvider.class);
        NvidiaAIAnalysisProvider nvidiaProvider =
                mock(NvidiaAIAnalysisProvider.class);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> configuration.aiAnalysisProvider(
                        properties,
                        mockProvider,
                        jevProvider,
                        nvidiaProvider
                )
        );

        assertEquals(
                "Jev AI analysis provider is not available for all required capabilities.",
                exception.getMessage()
        );
    }
}
