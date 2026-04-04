package com.jap.api.exception;

public class ConcurrencyLimitReachedException extends ApiException {
    private final int maxConcurrent;

    public ConcurrencyLimitReachedException(int maxConcurrent) {
        super("CONCURRENCY_LIMIT_REACHED", 
              "Agent pool exhausted, max=" + maxConcurrent + " active tasks");
        this.maxConcurrent = maxConcurrent;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }
}
