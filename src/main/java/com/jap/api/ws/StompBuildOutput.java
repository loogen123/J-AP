package com.jap.api.ws;

import java.time.Instant;

public record StompBuildOutput(
    String line,
    String stream,
    boolean isError
) implements StompMessage {

    @Override
    public String type() {
        return "BUILD_OUTPUT";
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
        return new Data(line, stream, isError);
    }

    public record Data(String line, String stream, boolean isError) {}
}
