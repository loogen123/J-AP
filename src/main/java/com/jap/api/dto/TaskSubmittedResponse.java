package com.jap.api.dto;

import com.jap.core.state.AgentStatus;

import java.time.Instant;

public record TaskSubmittedResponse(
    String taskId,
    AgentStatus status,
    Instant createdAt,
    TaskLinks _links
) {
    public static TaskSubmittedResponse of(String taskId, AgentStatus status, String baseUrl) {
        return new TaskSubmittedResponse(
            taskId,
            status,
            Instant.now(),
            TaskLinks.forTask(taskId, baseUrl)
        );
    }
}
