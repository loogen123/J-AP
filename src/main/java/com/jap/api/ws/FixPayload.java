package com.jap.api.ws;

public record FixPayload(
    int round,
    String errorCategory,
    String targetFile,
    boolean fixApplied,
    String llmModel,
    double llmTemperature,
    String patchType,
    long backoffBeforeNextMs
) {}
