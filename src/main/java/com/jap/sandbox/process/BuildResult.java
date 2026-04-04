package com.jap.sandbox.process;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public record BuildResult(
    boolean success,
    int exitCode,
    String output,
    String errorOutput,
    Duration duration,
    List<CompileError> errors,
    List<String> warnings
) {
    public static BuildResult success(Duration duration, String output) {
        return new BuildResult(true, 0, output, "", duration, List.of(), List.of());
    }

    public static BuildResult failure(int exitCode, String output, String errorOutput, 
                                       Duration duration, List<CompileError> errors) {
        return new BuildResult(false, exitCode, output, errorOutput, duration, errors, List.of());
    }

    public static BuildResult failure(int exitCode, String output, String errorOutput) {
        return new BuildResult(false, exitCode, output, errorOutput, Duration.ZERO, List.of(), List.of());
    }

    public static BuildResult timeout(String output) {
        return new BuildResult(false, -1, output, "Build timed out", Duration.ZERO, List.of(), List.of());
    }

    public boolean hasErrors() {
        return !success && !errors.isEmpty();
    }

    public int getErrorCount() {
        return errors.size();
    }
}
