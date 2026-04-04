package com.jap.api.ws;

import java.time.Instant;

public record StompStageChanged(
    String type,
    String eventId,
    Instant timestamp,
    StageChangedPayload data
) implements StompMessage {

    public StompStageChanged(String from, String to, String transition, int agentCount, int activeAgents) {
        this(
            "STAGE_CHANGED",
            StompMessage.nextEventId(),
            Instant.now(),
            new StageChangedPayload(from, to, transition, agentCount, activeAgents)
        );
    }
}
