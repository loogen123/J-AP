package com.jap.sandbox.exception;

import com.jap.api.exception.ApiException;
import com.jap.sandbox.model.SandboxOperation;
import com.jap.sandbox.model.SandboxViolation;
import com.jap.sandbox.model.SecurityLevel;

public class SandboxViolationException extends ApiException {

    private final SandboxViolation violation;

    public SandboxViolationException(SandboxOperation operation, String message, SecurityLevel severity) {
        super("E05", message);
        this.violation = SandboxViolation.of(operation, "unknown", message, severity);
    }

    public SandboxViolationException(SandboxOperation operation, String attemptedPath, 
                                      String message, SecurityLevel severity) {
        super("E05", message);
        this.violation = SandboxViolation.of(operation, attemptedPath, message, severity);
    }

    public SandboxViolationException(SandboxViolation violation) {
        super("E05", violation.reason());
        this.violation = violation;
    }

    public SandboxViolation getViolation() {
        return violation;
    }

    public SecurityLevel getSeverity() {
        return violation.severity();
    }

    public SandboxOperation getOperation() {
        return violation.operation();
    }

    public String getAttemptedPath() {
        return violation.attemptedPath();
    }

    public static SandboxViolationException pathTraversal(String path) {
        return new SandboxViolationException(
            SandboxOperation.READ,
            path,
            "Path traversal detected: " + path,
            SecurityLevel.CRITICAL
        );
    }

    public static SandboxViolationException symlinkAttack(String path) {
        return new SandboxViolationException(
            SandboxOperation.READ,
            path,
            "Symlink attack detected: " + path,
            SecurityLevel.CRITICAL
        );
    }

    public static SandboxViolationException outsideSandbox(String path) {
        return new SandboxViolationException(
            SandboxOperation.READ,
            path,
            "Path escapes sandbox root: " + path,
            SecurityLevel.CRITICAL
        );
    }

    public static SandboxViolationException invalidPath(String path, Throwable cause) {
        return new SandboxViolationException(
            SandboxOperation.READ,
            path,
            "Invalid path: " + path + " - " + cause.getMessage(),
            SecurityLevel.HIGH
        );
    }
}
