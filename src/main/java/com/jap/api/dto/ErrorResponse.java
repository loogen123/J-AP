package com.jap.api.dto;

import java.time.Instant;

public record ErrorResponse(
    String code,
    String message,
    String field,
    Instant timestamp,
    String traceId,
    Object details
) {
    public static ErrorResponse validation(String field, String message) {
        return new ErrorResponse(
            "VALIDATION_ERROR",
            message,
            field,
            Instant.now(),
            null,
            null
        );
    }

    public static ErrorResponse notFound(String resource, String id) {
        return new ErrorResponse(
            "TASK_NOT_FOUND",
            resource + " not found with id: " + id,
            null,
            Instant.now(),
            null,
            null
        );
    }

    public static ErrorResponse conflict(String code, String message) {
        return new ErrorResponse(
            code,
            message,
            null,
            Instant.now(),
            null,
            null
        );
    }

    public static ErrorResponse internal(String message, String traceId) {
        return new ErrorResponse(
            "INTERNAL_ERROR",
            message,
            null,
            Instant.now(),
            traceId,
            null
        );
    }

    public static ErrorResponse sandboxViolation(String message) {
        return new ErrorResponse(
            "SANDBOX_VIOLATION",
            message,
            null,
            Instant.now(),
            null,
            null
        );
    }

    public static ErrorResponse concurrencyLimitReached(int max) {
        return new ErrorResponse(
            "CONCURRENCY_LIMIT_REACHED",
            "Agent pool exhausted, max=" + max + " active tasks",
            null,
            Instant.now(),
            null,
            null
        );
    }

    public static ErrorResponse healingExhausted(String taskId, int rounds) {
        return new ErrorResponse(
            "HEALING_EXHAUSTED",
            "Self-healing exhausted after " + rounds + " rounds for task: " + taskId,
            null,
            Instant.now(),
            null,
            null
        );
    }
}
