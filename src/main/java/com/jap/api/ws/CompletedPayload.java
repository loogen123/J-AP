package com.jap.api.ws;

public record CompletedPayload(
    String status,
    String outcome,
    long totalDurationMs,
    int healingRounds,
    int filesGenerated,
    int testsPassed,
    int testsFailed,
    long buildTimeFinalMs,
    String summary
) {}
