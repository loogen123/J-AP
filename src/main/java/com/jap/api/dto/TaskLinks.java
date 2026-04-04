package com.jap.api.dto;

public record TaskLinks(
    String self,
    String status,
    String logs,
    String events,
    String files,
    String ws
) {
    public static TaskLinks forTask(String taskId, String baseUrl) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return new TaskLinks(
            normalizedBase + "/api/v1/tasks/" + taskId,
            normalizedBase + "/api/v1/tasks/" + taskId + "/status",
            normalizedBase + "/api/v1/tasks/" + taskId + "/logs",
            normalizedBase + "/api/v1/tasks/" + taskId + "/events",
            normalizedBase + "/api/v1/tasks/" + taskId + "/files",
            normalizedBase + "/ws/tasks/" + taskId
        );
    }
}
