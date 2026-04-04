package com.jap.api.ws;

import java.time.Instant;

public record StompFileGenerated(
    String type,
    String eventId,
    Instant timestamp,
    FilePayload data
) implements StompMessage {

    public StompFileGenerated(String path, long size, String action, String language) {
        this(
            "FILE_GENERATED",
            StompMessage.nextEventId(),
            Instant.now(),
            new FilePayload(path, size, action, language)
        );
    }
}
