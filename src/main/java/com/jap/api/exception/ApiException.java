package com.jap.api.exception;

public class ApiException extends RuntimeException {
    private final String errorCode;
    private final String field;

    public ApiException(String message) {
        super(message);
        this.errorCode = "INTERNAL_ERROR";
        this.field = null;
    }

    public ApiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.field = null;
    }

    public ApiException(String errorCode, String message, String field) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getField() {
        return field;
    }
}
