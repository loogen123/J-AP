package com.jap.api.ws;

import java.time.Instant;

public record StompFixAttempted(
    String type,
    String eventId,
    Instant timestamp,
    FixPayload data
) implements StompMessage {

    public StompFixAttempted(int round, String errorCategory, String targetFile, 
                             boolean fixApplied, String llmModel, double llmTemperature,
                             String patchType, long backoffBeforeNextMs) {
        this(
            "FIX_ATTEMPTED",
            StompMessage.nextEventId(),
            Instant.now(),
            new FixPayload(round, errorCategory, targetFile, fixApplied, llmModel, 
                          llmTemperature, patchType, backoffBeforeNextMs)
        );
    }
}
