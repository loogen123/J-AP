package com.jap.api.ws;

import java.time.Instant;

public record StompPipelineCompleted(
    String type,
    String eventId,
    Instant timestamp,
    CompletedPayload data
) implements StompMessage {

    public StompPipelineCompleted(String status, String outcome, long totalDurationMs,
                                  int healingRounds, int filesGenerated, int testsPassed,
                                  int testsFailed, long buildTimeFinalMs, String summary) {
        this(
            "PIPELINE_COMPLETED",
            StompMessage.nextEventId(),
            Instant.now(),
            new CompletedPayload(status, outcome, totalDurationMs, healingRounds, 
                                filesGenerated, testsPassed, testsFailed, buildTimeFinalMs, summary)
        );
    }
}
