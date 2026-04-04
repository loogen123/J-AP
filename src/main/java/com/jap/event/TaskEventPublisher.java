package com.jap.event;

import com.jap.api.ws.StompMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public TaskEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(String taskId, StompMessage message) {
        String destination = "/topic/tasks/" + taskId;
        messagingTemplate.convertAndSend(destination, message);
    }

    public void publishStageChanged(String taskId, String from, String to, String transition) {
        publish(taskId, new com.jap.api.ws.StompStageChanged(from, to, transition, 1, 1));
    }

    public void publishLogAdded(String taskId, String logId, String logType, String stage, 
                                String title, String summary, String color) {
        publish(taskId, new com.jap.api.ws.StompLogAdded(logId, logType, stage, title, summary, color));
    }

    public void publishProgressUpdated(String taskId, int percentage, String stageName, String detail) {
        publish(taskId, new com.jap.api.ws.StompProgressUpdated(percentage, stageName, detail));
    }

    public void publishFileGenerated(String taskId, String path, long size, String language) {
        publish(taskId, new com.jap.api.ws.StompFileGenerated(path, size, "CREATED", language));
    }

    public void publishErrorDetected(String taskId, String category, String severity, String file,
                                     Integer line, String message, String suggestion) {
        publish(taskId, new com.jap.api.ws.StompErrorDetected(
            category, severity, file, line, message, suggestion, null
        ));
    }

    public void publishFixAttempted(String taskId, int round, String errorCategory, String targetFile,
                                    boolean fixApplied) {
        publish(taskId, new com.jap.api.ws.StompFixAttempted(
            round, errorCategory, targetFile, fixApplied, "gpt-4o", 0.1, "FULL_REPLACE", 1000L
        ));
    }

    public void publishPipelineCompleted(String taskId, String outcome, long totalDurationMs,
                                         int healingRounds, int filesGenerated, int testsPassed) {
        publish(taskId, new com.jap.api.ws.StompPipelineCompleted(
            "COMPLETE", outcome, totalDurationMs, healingRounds, filesGenerated, 
            testsPassed, 0, 3891L, 
            "Self-Healing succeeded in " + healingRounds + " rounds"
        ));
    }

    public void publishManualInterventionRequired(String taskId, String reason, String summary) {
        publish(taskId, new com.jap.api.ws.StompManualInterventionRequired(
            reason, "/api/v1/tasks/" + taskId + "/context", summary, 
            com.jap.api.ws.PreservedContext.ALL
        ));
    }

    public void publishDesignComplete(String taskId, int designFilesCount, java.util.List<String> designFiles) {
        publish(taskId, new com.jap.api.ws.StompDesignComplete(designFilesCount, designFiles));
    }
}
