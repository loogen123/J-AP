package com.jap.api.ws;

public record InterventionPayload(
    String reason,
    String contextUrl,
    String humanReadableSummary,
    PreservedContext preservedContext
) {}
