package com.jap.api.dto;

public record TaskStatistics(
    long durationSeconds,
    TokenUsage llmTokenUsage,
    int mavenBuildCount
) {
    public static TaskStatistics empty() {
        return new TaskStatistics(0, new TokenUsage(0, 0), 0);
    }
}
