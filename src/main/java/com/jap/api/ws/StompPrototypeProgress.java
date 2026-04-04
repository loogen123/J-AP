package com.jap.api.ws;

import java.time.Instant;

public record StompPrototypeProgress(
    int charsWritten,
    String lastChunk,
    int percentage,
    boolean complete,
    boolean partial
) implements StompMessage {

    @Override
    public String type() {
        return "PROTOTYPE_GEN_PROGRESS";
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
        return new Data(charsWritten, lastChunk, percentage, complete, partial);
    }

    public record Data(
        int charsWritten,
        String lastChunk,
        int percentage,
        boolean complete,
        boolean partial
    ) {}
}
