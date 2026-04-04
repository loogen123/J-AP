package com.jap.api.ws;

import java.time.Instant;
import java.util.List;

public record StompDesignComplete(
    int designFilesCount,
    List<String> designFiles
) implements StompMessage {

    @Override
    public String type() {
        return "DESIGN_COMPLETE";
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
        return new Data(designFilesCount, designFiles);
    }

    public record Data(int designFilesCount, List<String> designFiles) {}
}
