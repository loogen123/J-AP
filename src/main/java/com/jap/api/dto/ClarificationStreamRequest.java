package com.jap.api.dto;

import java.util.List;

public record ClarificationStreamRequest(
    String requirement,
    List<ClarificationMessage> history,
    String userMessage,
    String focus,
    TaskLlmConfig llm
) {
}
