package com.jap.api.dto;

public record HealingStatus(
    String errorCategory,
    int currentRetry,
    int maxRetries,
    String lastError,
    String lastErrorFile,
    Integer lastErrorLine,
    long nextBackoffMs
) {
    public static HealingStatus of(String errorCategory, int currentRetry, int maxRetries) {
        return new HealingStatus(
            errorCategory,
            currentRetry,
            maxRetries,
            null,
            null,
            null,
            calculateBackoff(currentRetry)
        );
    }

    public HealingStatus withError(String message, String file, Integer line) {
        return new HealingStatus(
            errorCategory,
            currentRetry,
            maxRetries,
            message,
            file,
            line,
            nextBackoffMs
        );
    }

    private static long calculateBackoff(int retry) {
        return 1000L * (1L << retry);
    }
}
