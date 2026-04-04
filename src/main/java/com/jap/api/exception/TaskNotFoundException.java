package com.jap.api.exception;

public class TaskNotFoundException extends ApiException {
    public TaskNotFoundException(String taskId) {
        super("TASK_NOT_FOUND", "Task not found with id: " + taskId);
    }

    public TaskNotFoundException(String resource, String id) {
        super("TASK_NOT_FOUND", resource + " not found with id: " + id);
    }
}
