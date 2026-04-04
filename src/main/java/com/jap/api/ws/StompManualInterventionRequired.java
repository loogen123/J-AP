package com.jap.api.ws;

import java.time.Instant;

public record StompManualInterventionRequired(
    String type,
    String eventId,
    Instant timestamp,
    InterventionPayload data
) implements StompMessage {

    public StompManualInterventionRequired(String reason, String contextUrl, 
                                           String humanReadableSummary, PreservedContext preservedContext) {
        this(
            "MANUAL_INTERVENTION_REQUIRED",
            StompMessage.nextEventId(),
            Instant.now(),
            new InterventionPayload(reason, contextUrl, humanReadableSummary, preservedContext)
        );
    }
}
