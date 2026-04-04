package com.jap.sandbox.process;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CompileError(
    @JsonProperty String filePath,
    @JsonProperty int lineNumber,
    @JsonProperty int columnNumber,
    @JsonProperty String errorCode,
    @JsonProperty String message,
    @JsonProperty ErrorSeverity severity
) {
    public enum ErrorSeverity {
        ERROR,
        WARNING,
        INFO
    }

    public static CompileError of(String filePath, int line, int column, 
                                   String errorCode, String message) {
        return new CompileError(filePath, line, column, errorCode, message, 
                                ErrorSeverity.ERROR);
    }

    public static CompileError warning(String filePath, int line, int column,
                                        String errorCode, String message) {
        return new CompileError(filePath, line, column, errorCode, message,
                                ErrorSeverity.WARNING);
    }

    public String toFormattedString() {
        return String.format("%s:%d:%d: %s: %s", 
            filePath, lineNumber, columnNumber, errorCode, message);
    }
}
