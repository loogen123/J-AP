package com.jap.api.ws;

import java.time.Instant;
import java.util.Optional;

public sealed interface StompMessage
    permits StompStageChanged, StompLogAdded, StompProgressUpdated,
            StompFileGenerated, StompErrorDetected, StompFixAttempted,
            StompPipelineCompleted, StompManualInterventionRequired,
            StompDesignComplete, StompBuildOutput, StompProgressHeartbeat,
            StompPrototypeProgress {

    String type();
    String eventId();
    Instant timestamp();
    Object data();

    static String nextEventId() {
        return "evt-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }
}
