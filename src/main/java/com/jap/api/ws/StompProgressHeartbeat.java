package com.jap.api.ws;

import java.time.Instant;

public record StompProgressHeartbeat(
    String stage,
    int charsGenerated,
    String currentAction,
    int percentage
) implements StompMessage {

    @Override
    public String type() {
        return "PROGRESS_HEARTBEAT";
    }

    @Override
    public String eventId() {
        return StompMessage.nextEventId();
    }

    @Override
    public Instant timestamp() {
        return Instant.now();
    }

    @Override
    public Object data() {
        return new Data(stage, charsGenerated, currentAction, percentage);
    }

    public record Data(String stage, int charsGenerated, String currentAction, int percentage) {}
}
