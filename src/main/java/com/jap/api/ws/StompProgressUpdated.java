package com.jap.api.ws;

import java.time.Instant;

public record StompProgressUpdated(
    String type,
    String eventId,
    Instant timestamp,
    ProgressPayload data
) implements StompMessage {

    public StompProgressUpdated(int percentage, String stageName, String detail) {
        this(
            "PROGRESS_UPDATED",
            StompMessage.nextEventId(),
            Instant.now(),
            new ProgressPayload(percentage, stageName, detail)
        );
    }
}
