package com.jap.api.ws;

import java.util.Optional;

public record ErrorPayload(
    String category,
    String severity,
    String file,
    Integer line,
    String message,
    Optional<String> suggestion,
    Optional<String> sourceSnippet
) {}
