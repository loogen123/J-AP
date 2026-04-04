package com.jap.sandbox.model;

import java.time.Instant;

public record SandboxViolation(
    SandboxOperation operation,
    String attemptedPath,
    String reason,
    SecurityLevel severity,
    Instant timestamp,
    String taskId
) {
    public static SandboxViolation of(SandboxOperation operation, String attemptedPath, 
                                       String reason, SecurityLevel severity) {
        return new SandboxViolation(operation, attemptedPath, reason, severity, Instant.now(), null);
    }

    public static SandboxViolation of(SandboxOperation operation, String attemptedPath,
                                       String reason, SecurityLevel severity, String taskId) {
        return new SandboxViolation(operation, attemptedPath, reason, severity, Instant.now(), taskId);
    }

    public String toLogMessage() {
        return String.format("[%s] SandboxViolation: operation=%s, path=%s, reason=%s, severity=%s, taskId=%s",
            timestamp, operation.getCode(), attemptedPath, reason, severity.getCode(), taskId);
    }
}
