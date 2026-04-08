package com.jap.api;

import com.jap.api.dto.*;
import com.jap.api.exception.TaskNotFoundException;
import com.jap.config.JapConfigManager;
import com.jap.config.JapProperties;
import com.jap.core.agent.AgentOrchestrator;
import com.jap.core.agent.JapAgent;
import com.jap.core.state.AgentStatus;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/tasks")
public class JapController {

    private static final Logger log = LoggerFactory.getLogger(JapController.class);

    private final Map<String, AgentContext> runningTasks = new ConcurrentHashMap<>();
    private final JapProperties japProperties;
    private final AgentOrchestrator agentOrchestrator;
    private final JapConfigManager configManager;

    public JapController(JapProperties japProperties, AgentOrchestrator agentOrchestrator, JapConfigManager configManager) {
        this.japProperties = japProperties;
        this.agentOrchestrator = agentOrchestrator;
        this.configManager = configManager;
    }

    @PostMapping
    public ResponseEntity<TaskSubmittedResponse> submitTask(
            @Valid @RequestBody SubmitRequest request,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost
    ) {
        return submitTaskInternal(request, forwardedHost, false);
    }

    @PostMapping("/design-only")
    public ResponseEntity<TaskSubmittedResponse> submitDesignOnly(
            @Valid @RequestBody SubmitRequest request,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost
    ) {
        return submitTaskInternal(request, forwardedHost, true);
    }

    private ResponseEntity<TaskSubmittedResponse> submitTaskInternal(
            SubmitRequest request, 
            String forwardedHost,
            boolean designOnly
    ) {
        if (request.llm() == null || request.llm().apiKey() == null || request.llm().apiKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task-scoped llm.apiKey is required");
        }

        String taskId = generateTaskId();
        log.info("Received new task: {} (designOnly={}) with requirement: {}", taskId, designOnly,
                request.requirement().substring(0, Math.min(50, request.requirement().length())) + "...");

        String baseUrl = determineBaseUrl(forwardedHost);
        
        AgentContext context = new AgentContext(
            taskId,
            request.requirement(),
            request.options() != null ? request.options() : TaskOptions.DEFAULTS,
            request.faultSimulation() != null ? request.faultSimulation() : FaultSimulation.DISABLED
        );
        runningTasks.put(taskId, context);
        configManager.registerTaskLlmConfig(taskId, request.llm());

        if (designOnly) {
            agentOrchestrator.submitDesignOnly(taskId, request.requirement(), context.options(), context.faultSimulation())
                .thenAccept(result -> {
                    log.info("[{}] Agent completed with outcome: {}", taskId, result.outcome());
                    context.updateStatus(mapOutcomeToStatus(result.outcome()));
                    configManager.clearTaskLlmConfig(taskId);
                });
        } else {
            agentOrchestrator.submit(taskId, request.requirement(), context.options(), context.faultSimulation())
                .thenAccept(result -> {
                    log.info("[{}] Agent completed with outcome: {}", taskId, result.outcome());
                    context.updateStatus(mapOutcomeToStatus(result.outcome()));
                    configManager.clearTaskLlmConfig(taskId);
                });
        }

        AgentStatus initialStatus = designOnly ? AgentStatus.INTENT_ANALYSIS : AgentStatus.INTENT_ANALYSIS;
        TaskSubmittedResponse response = TaskSubmittedResponse.of(taskId, initialStatus, baseUrl);
        
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .header("Location", "/api/v1/tasks/" + taskId)
                .body(response);
    }

    @PostMapping("/{taskId}/resume")
    public ResponseEntity<Map<String, Object>> resumeTask(@PathVariable String taskId) {
        AgentContext context = runningTasks.get(taskId);
        if (context == null) {
            throw new TaskNotFoundException("Task", taskId);
        }

        boolean resumed = agentOrchestrator.resume(taskId);
        
        if (resumed) {
            context.updateStatus(AgentStatus.CODE_GENERATION);
            
            Map<String, Object> response = Map.of(
                "taskId", taskId,
                "previousStatus", AgentStatus.WAITING_FOR_APPROVAL.name(),
                "newStatus", AgentStatus.CODE_GENERATION.name(),
                "resumedAt", Instant.now().toString(),
                "message", "Task resumed. Continuing with implementation..."
            );
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity
                .badRequest()
                .body(Map.of(
                    "error", "Task is not in WAITING_FOR_APPROVAL status or pause point not found",
                    "taskId", taskId
                ));
        }
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskStatusResponse> getTask(@PathVariable String taskId) {
        AgentContext context = runningTasks.get(taskId);
        if (context == null) {
            throw new TaskNotFoundException("Task", taskId);
        }
        
        TaskStatusResponse response = context.toTaskStatusResponse();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{taskId}/status")
    public ResponseEntity<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        AgentContext context = runningTasks.get(taskId);
        if (context == null) {
            throw new TaskNotFoundException("Task", taskId);
        }

        TaskStatusResponse response = context.toTaskStatusResponse();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{taskId}/logs")
    public ResponseEntity<LogPageResponse> getTaskLogs(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "100") int limit
    ) {
        AgentContext context = runningTasks.get(taskId);
        if (context == null) {
            throw new TaskNotFoundException("Task", taskId);
        }

        LogPageResponse response = new LogPageResponse(
            taskId,
            context.logs().size(),
            offset,
            limit,
            false,
            context.logs().stream().skip(offset).limit(limit).toList()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{taskId}/files")
    public ResponseEntity<FileTreeResponse> getTaskFiles(@PathVariable String taskId) {
        AgentContext context = runningTasks.get(taskId);
        if (context == null) {
            throw new TaskNotFoundException("Task", taskId);
        }

        FileTreeResponse response = new FileTreeResponse(
            taskId,
            "generated-workspace",
            context.generatedFiles().stream()
                .map(path -> new FileNode(
                    path.substring(path.lastIndexOf('/') + 1),
                    FileNode.FileType.FILE,
                    path,
                    512,
                    Instant.now(),
                    Optional.empty()
                ))
                .toList(),
            context.generatedFiles().size(),
            context.generatedFiles().size() * 512L
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{taskId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelTask(
            @PathVariable String taskId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        AgentContext context = runningTasks.get(taskId);
        if (context == null) {
            throw new TaskNotFoundException("Task", taskId);
        }

        String reason = body != null ? body.get("reason") : "User cancelled";
        agentOrchestrator.cancel(taskId);
        configManager.clearTaskLlmConfig(taskId);
        context.cancel(reason);

        Map<String, Object> response = Map.of(
            "taskId", taskId,
            "previousStatus", context.currentStatus().name(),
            "newStatus", AgentStatus.CANCELLED.name(),
            "cancelledAt", Instant.now().toString(),
            "gracefulShutdown", true,
            "message", "Task cancelled successfully. Agent thread interrupted."
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/agents/stats")
    public ResponseEntity<Map<String, Object>> getAgentStats() {
        Map<String, Object> stats = Map.of(
            "activeCount", agentOrchestrator.getActiveCount(),
            "maxConcurrent", japProperties.agent().maxConcurrent(),
            "availableSlots", japProperties.agent().maxConcurrent() - agentOrchestrator.getActiveCount()
        );
        return ResponseEntity.ok(stats);
    }

    private String generateTaskId() {
        return "task-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String determineBaseUrl(String forwardedHost) {
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            return "http://" + forwardedHost;
        }
        return "http://localhost:8080";
    }

    private AgentStatus mapOutcomeToStatus(String outcome) {
        return switch (outcome) {
            case "SUCCESS", "HEALED" -> AgentStatus.COMPLETE;
            case "CANCELLED" -> AgentStatus.CANCELLED;
            case "MANUAL_INTERVENTION" -> AgentStatus.MANUAL_INTERVENTION;
            default -> AgentStatus.FAILED;
        };
    }

    public static class AgentContext {
        private final String taskId;
        private final String requirement;
        private final TaskOptions options;
        private final FaultSimulation faultSimulation;
        private volatile AgentStatus currentStatus;
        private final long createdAt;
        private final List<String> generatedFiles;
        private final List<LogEntry> logs;

        public AgentContext(String taskId, String requirement, TaskOptions options, FaultSimulation faultSimulation) {
            this.taskId = taskId;
            this.requirement = requirement;
            this.options = options;
            this.faultSimulation = faultSimulation;
            this.currentStatus = AgentStatus.IDLE;
            this.createdAt = System.currentTimeMillis();
            this.generatedFiles = new java.util.ArrayList<>();
            this.logs = new java.util.ArrayList<>();
        }

        public String taskId() { return taskId; }
        public String requirement() { return requirement; }
        public TaskOptions options() { return options; }
        public FaultSimulation faultSimulation() { return faultSimulation; }
        public AgentStatus currentStatus() { return currentStatus; }
        public List<String> generatedFiles() { return List.copyOf(generatedFiles); }
        public List<LogEntry> logs() { return List.copyOf(logs); }

        public void updateStatus(AgentStatus newStatus) {
            this.currentStatus = newStatus;
        }

        public void cancel(String reason) {
            this.currentStatus = AgentStatus.CANCELLED;
        }

        public void addGeneratedFile(String path) {
            generatedFiles.add(path);
        }

        public void addLog(LogEntry entry) {
            logs.add(entry);
        }

        public TaskStatusResponse toTaskStatusResponse() {
            return new TaskStatusResponse(
                taskId,
                currentStatus,
                currentStatus.name(),
                ProgressInfo.of(calculateCompletedStages(), currentStatus.name()),
                Optional.empty(),
                List.of(),
                generatedFiles,
                Optional.of(new TaskStatistics(
                    (System.currentTimeMillis() - createdAt) / 1000,
                    new TokenUsage(0, 0),
                    0
                ))
            );
        }

        private int calculateCompletedStages() {
            return switch (currentStatus) {
                case IDLE -> 0;
                case INTENT_ANALYSIS -> 0;
                case DESIGN -> 1;
                case WAITING_FOR_APPROVAL -> 2;
                case CODE_GENERATION -> 3;
                case BUILD_TEST -> 4;
                case BUG_FIX -> 4;
                case COMPLETE -> 7;
                case MANUAL_INTERVENTION -> 6;
                case FAILED -> 0;
                case CANCELLED -> 0;
            };
        }
    }
}
