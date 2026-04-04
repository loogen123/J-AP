package com.jap.api.dto;

public record ProgressInfo(
    int totalStages,
    int completedStages,
    String currentStageName,
    int percentage
) {
    public static ProgressInfo initial() {
        return new ProgressInfo(5, 0, null, 0);
    }

    public static ProgressInfo of(int completed, String stageName) {
        int percentage = (completed * 100) / 5;
        return new ProgressInfo(5, completed, stageName, percentage);
    }

    public static ProgressInfo complete() {
        return new ProgressInfo(5, 5, null, 100);
    }
}
