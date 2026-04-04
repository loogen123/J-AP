package com.jap.api.ws;

import java.time.Instant;
import java.util.Optional;

public record StompLogAdded(
    String type,
    String eventId,
    Instant timestamp,
    LogAddedPayload data
) implements StompMessage {

    public StompLogAdded(String logId, String logType, String stage, String title, String summary, String color) {
        this(
            "LOG_ADDED",
            StompMessage.nextEventId(),
            Instant.now(),
            new LogAddedPayload(logId, logType, Optional.ofNullable(stage), title, summary, color)
        );
    }
}
