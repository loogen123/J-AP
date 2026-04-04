package com.jap.api.ws;

import java.time.Instant;
import java.util.Optional;

public record StompErrorDetected(
    String type,
    String eventId,
    Instant timestamp,
    ErrorPayload data
) implements StompMessage {

    public StompErrorDetected(String category, String severity, String file, Integer line, 
                              String message, String suggestion, String sourceSnippet) {
        this(
            "ERROR_DETECTED",
            StompMessage.nextEventId(),
            Instant.now(),
            new ErrorPayload(
                category, 
                severity, 
                file, 
                line, 
                message, 
                Optional.ofNullable(suggestion), 
                Optional.ofNullable(sourceSnippet)
            )
        );
    }
}
