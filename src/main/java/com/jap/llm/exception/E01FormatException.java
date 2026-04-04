package com.jap.llm.exception;

public class E01FormatException extends RuntimeException {

    private final String rawOutput;
    private final int retryCount;

    public E01FormatException(String message, String rawOutput) {
        super(message);
        this.rawOutput = rawOutput;
        this.retryCount = 0;
    }

    public E01FormatException(String message, String rawOutput, int retryCount) {
        super(message);
        this.rawOutput = rawOutput;
        this.retryCount = retryCount;
    }

    public E01FormatException(String message, String rawOutput, Throwable cause) {
        super(message, cause);
        this.rawOutput = rawOutput;
        this.retryCount = 0;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public E01FormatException withIncrementedRetry() {
        return new E01FormatException(getMessage(), rawOutput, retryCount + 1);
    }
}
