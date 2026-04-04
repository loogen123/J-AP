package com.jap.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record LogEntry(
    String id,
    LogType type,
    Optional<String> stage,
    Instant timestamp,
    String title,
    String message,
    Optional<String> details,
    Map<String, Object> metadata
) {
    public enum LogType {
        SYSTEM, INFO, INNER, OUTER, SUCCESS, ERROR, WARN, FIX
    }

    public static LogEntry of(String id, LogType type, String stage, String title, String message) {
        return new LogEntry(
            id,
            type,
            Optional.ofNullable(stage),
            Instant.now(),
            title,
            message,
            Optional.empty(),
            Map.of()
        );
    }

    public static LogEntry system(String title, String message) {
        return of("log-" + System.nanoTime(), LogType.SYSTEM, null, title, message);
    }

    public static LogEntry info(String stage, String title, String message) {
        return of("log-" + System.nanoTime(), LogType.INFO, stage, title, message);
    }

    public static LogEntry inner(String stage, String title, String message) {
        return of("log-" + System.nanoTime(), LogType.INNER, stage, title, message);
    }

    public static LogEntry outer(String stage, String title, String message) {
        return of("log-" + System.nanoTime(), LogType.OUTER, stage, title, message);
    }

    public static LogEntry success(String stage, String title, String message) {
        return of("log-" + System.nanoTime(), LogType.SUCCESS, stage, title, message);
    }

    public static LogEntry error(String stage, String title, String message) {
        return of("log-" + System.nanoTime(), LogType.ERROR, stage, title, message);
    }

    public static LogEntry fix(String stage, String title, String message) {
        return of("log-" + System.nanoTime(), LogType.FIX, stage, title, message);
    }
}
