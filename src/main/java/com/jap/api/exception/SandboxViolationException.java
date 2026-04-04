package com.jap.api.exception;

public class SandboxViolationException extends ApiException {
    public SandboxViolationException(String operation, String message) {
        super("SANDBOX_VIOLATION", operation + ": " + message);
    }

    public SandboxViolationException(String message) {
        super("SANDBOX_VIOLATION", message);
    }
}
