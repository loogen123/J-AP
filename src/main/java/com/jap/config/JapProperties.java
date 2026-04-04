package com.jap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "jap")
public record JapProperties(
    SandboxConfig sandbox,
    AgentConfig agent,
    LlmConfig llm
) {
    public record SandboxConfig(
        String baseDir,
        ProcessConfig process
    ) {
        public record ProcessConfig(
            Duration maxExecutionTime,
            long maxMemoryMb,
            List<String> allowedNetworkHosts
        ) {}
    }
    
    public record AgentConfig(
        int maxConcurrent,
        int defaultMaxRetries
    ) {}

    public record LlmConfig(
        String baseUrl,
        String apiKey,
        String modelName,
        Double temperature,
        Integer maxTokens,
        Integer timeoutSeconds,
        Boolean logRequests,
        Boolean logResponses
    ) {}
}
