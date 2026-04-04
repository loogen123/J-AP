package com.jap.api.ws;

public record ProgressPayload(
    int percentage,
    String stageName,
    String detail
) {}
