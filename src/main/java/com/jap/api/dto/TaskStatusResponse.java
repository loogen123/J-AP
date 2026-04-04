package com.jap.api.dto;

import com.jap.core.state.AgentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record TaskStatusResponse(
    String taskId,
    AgentStatus status,
    String currentStage,
    ProgressInfo progress,
    Optional<HealingStatus> healing,
    List<TimelineEntry> timeline,
    List<String> generatedFiles,
    Optional<TaskStatistics> statistics
) {
    public static TaskStatusResponse initial(String taskId) {
        return new TaskStatusResponse(
            taskId,
            AgentStatus.IDLE,
            null,
            ProgressInfo.initial(),
            Optional.empty(),
            List.of(),
            List.of(),
            Optional.empty()
        );
    }
}
