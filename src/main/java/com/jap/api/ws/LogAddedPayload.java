package com.jap.api.ws;

import java.util.Optional;

public record LogAddedPayload(
    String logId,
    String logType,
    Optional<String> stage,
    String title,
    String summary,
    String color
) {}
