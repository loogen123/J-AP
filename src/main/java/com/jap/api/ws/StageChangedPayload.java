package com.jap.api.ws;

public record StageChangedPayload(
    String from,
    String to,
    String transition,
    int agentCount,
    int activeAgents
) {}
