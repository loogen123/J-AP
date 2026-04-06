package com.jap.api.dto;

public record TaskLlmConfig(
    String baseUrl,
    String apiKey,
    String modelName,
    Double temperature,
    Integer maxTokens,
    Integer timeoutSeconds
) {
}

