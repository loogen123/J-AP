package com.jap.api.dto;

import java.time.Instant;
import java.util.Optional;

public record TimelineEntry(
    String stage,
    TimelineStatus status,
    long durationMs,
    Instant occurredAt,
    Optional<Integer> round,
    Optional<String> error
) {
    public enum TimelineStatus {
        STARTED, COMPLETED, FAILED, IN_PROGRESS, SKIPPED
    }

    public static TimelineEntry started(String stage, Instant at) {
        return new TimelineEntry(
            stage,
            TimelineStatus.STARTED,
            0,
            at,
            Optional.empty(),
            Optional.empty()
        );
    }

    public static TimelineEntry completed(String stage, long durationMs, Instant at) {
        return new TimelineEntry(
            stage,
            TimelineStatus.COMPLETED,
            durationMs,
            at,
            Optional.empty(),
            Optional.empty()
        );
    }

    public static TimelineEntry failed(String stage, long durationMs, String errorCode, Instant at) {
        return new TimelineEntry(
            stage,
            TimelineStatus.FAILED,
            durationMs,
            at,
            Optional.empty(),
            Optional.of(errorCode)
        );
    }

    public static TimelineEntry inProgress(String stage, int round, long durationMs, Instant at) {
        return new TimelineEntry(
            stage,
            TimelineStatus.IN_PROGRESS,
            durationMs,
            at,
            Optional.of(round),
            Optional.empty()
        );
    }
}
