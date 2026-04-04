package com.jap.core.agent;

import com.jap.api.dto.FaultSimulation;
import com.jap.api.dto.TaskOptions;

import java.util.concurrent.CompletableFuture;

public interface AgentOrchestrator {

    CompletableFuture<JapAgent.JapAgentResult> submit(
        String taskId, 
        String requirement, 
        TaskOptions options, 
        FaultSimulation faultSimulation
    );

    CompletableFuture<JapAgent.JapAgentResult> submitDesignOnly(
        String taskId, 
        String requirement, 
        TaskOptions options, 
        FaultSimulation faultSimulation
    );

    boolean resume(String taskId);

    boolean cancel(String taskId);

    int getActiveCount();
}
