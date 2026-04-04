package com.jap.core.agent;

import com.jap.api.dto.FaultSimulation;
import com.jap.api.dto.TaskOptions;
import com.jap.api.ws.*;
import com.jap.config.JapProperties;
import com.jap.core.state.AgentStatus;
import com.jap.event.TaskEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MockAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MockAgentOrchestrator.class);

    private final ConcurrentHashMap<String, MockAgentTask> runningTasks = new ConcurrentHashMap<>();
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final ExecutorService virtualExecutor;
    private final int maxConcurrentAgents;
    private final TaskEventPublisher eventPublisher;

    private static final List<String> GENERATED_FILES = List.of(
        "src/main/java/com/example/entity/User.java",
        "src/main/java/com/example/repository/UserRepository.java",
        "src/main/java/com/example/service/UserService.java",
        "src/main/java/com/example/controller/UserController.java",
        "src/main/java/com/example/dto/UserDto.java",
        "src/main/resources/application.yml"
    );

    private static final Map<String, String> LOG_COLORS = Map.of(
        "SYSTEM", "#64748b",
        "INFO", "#3b82f6",
        "INNER", "#60a5fa",
        "OUTER", "#f59e0b",
        "SUCCESS", "#10b981",
        "ERROR", "#ef4444",
        "WARN", "#f59e0b",
        "FIX", "#8b5cf6"
    );

    public MockAgentOrchestrator(JapProperties properties, TaskEventPublisher eventPublisher) {
        this.maxConcurrentAgents = properties.agent().maxConcurrent();
        this.eventPublisher = eventPublisher;
        this.virtualExecutor = Executors.newFixedThreadPool(maxConcurrentAgents);
        log.info("MockAgentOrchestrator initialized: maxConcurrentAgents={} (using platform threads)", maxConcurrentAgents);
    }

    public CompletableFuture<MockAgentResult> submit(String taskId, String requirement, 
                                                      TaskOptions options, FaultSimulation faultSimulation) {
        if (activeCount.get() >= maxConcurrentAgents) {
            throw new com.jap.api.exception.ConcurrencyLimitReachedException(maxConcurrentAgents);
        }

        CompletableFuture<MockAgentResult> future = new CompletableFuture<>();
        
        Future<MockAgentResult> taskFuture = virtualExecutor.submit(() -> {
            activeCount.incrementAndGet();
            try {
                log.info("[{}] Mock Agent started (active={}/{})", taskId, activeCount.get(), maxConcurrentAgents);
                
                MockAgentTask task = new MockAgentTask(taskId, requirement, options, faultSimulation, eventPublisher);
                runningTasks.put(taskId, task);
                
                MockAgentResult result = task.run();
                future.complete(result);
                return result;
            } catch (Exception e) {
                log.error("[{}] Mock Agent failed", taskId, e);
                MockAgentResult failure = MockAgentResult.failed(taskId, e.getMessage());
                future.complete(failure);
                return failure;
            } finally {
                activeCount.decrementAndGet();
                runningTasks.remove(taskId);
                log.info("[{}] Mock Agent finished (active={})", taskId, activeCount.get());
            }
        });

        return future;
    }

    public boolean cancel(String taskId) {
        MockAgentTask task = runningTasks.get(taskId);
        if (task != null) {
            task.cancel();
            return true;
        }
        return false;
    }

    public int getActiveCount() {
        return activeCount.get();
    }

    public static class MockAgentTask {
        private final String taskId;
        private final String requirement;
        private final TaskOptions options;
        private final FaultSimulation faultSimulation;
        private final TaskEventPublisher publisher;
        private volatile boolean cancelled = false;
        private int logCounter = 0;

        public MockAgentTask(String taskId, String requirement, TaskOptions options,
                            FaultSimulation faultSimulation, TaskEventPublisher publisher) {
            this.taskId = taskId;
            this.requirement = requirement;
            this.options = options;
            this.faultSimulation = faultSimulation;
            this.publisher = publisher;
        }

        public MockAgentResult run() throws InterruptedException {
            long startTime = System.currentTimeMillis();

            publishStageChanged("IDLE", "INTENT_ANALYSIS", "StartAnalysis");
            publishLog("SYSTEM", null, "Agent Pipeline launched", taskId);
            sleep(200);

            publishLog("INFO", "INTENT_ANALYSIS", "INTENT_ANALYSIS started", "Analyzing requirement...");
            publishProgress(5, "Analyzing requirement...");
            sleep(500);

            publishLog("INNER", "INTENT_ANALYSIS", "Sending to AnalysisAiService", "JSON Schema constraint applied");
            sleep(800);

            publishLog("INNER", "INTENT_ANALYSIS", "RequirementSpec parsed successfully", "5 modules resolved");
            publishStageChanged("INTENT_ANALYSIS", "CODE_GENERATION", "AnalysisComplete");
            publishProgress(18, "Intent analysis complete");
            sleep(300);

            if (cancelled) return MockAgentResult.cancelled(taskId);

            publishLog("INFO", "CODE_GENERATION", "CODE_GENERATION started", "Parallel module generation via Virtual Threads");
            sleep(500);

            for (String file : GENERATED_FILES) {
                if (cancelled) return MockAgentResult.cancelled(taskId);
                String lang = file.endsWith(".java") ? "java" : "yaml";
                publisher.publishFileGenerated(taskId, file, 512, lang);
                sleep(400);
            }

            publishLog("INFO", "CODE_GENERATION", "All validations passed", "Jakarta EE 11 compliant");
            publishStageChanged("CODE_GENERATION", "BUILD_TEST", "GenerationComplete");
            publishProgress(72, "Code generation complete");
            sleep(300);

            if (cancelled) return MockAgentResult.cancelled(taskId);

            publishLog("INFO", "BUILD_TEST", "BUILD_TEST started", "SecureMavenExecutor in isolated process");
            publishLog("INFO", "BUILD_TEST", "Process sandbox active", "JVM args locked, MAVEN_OPTS cleared");
            sleep(1000);

            if (faultSimulation.enabled() && faultSimulation.faultType() != null) {
                return simulateFault(startTime);
            }

            publishLog("SUCCESS", "BUILD_TEST", "mvn clean test BUILD SUCCESS", "Tests run: 5, Failures: 0");
            publishStageChanged("BUILD_TEST", "COMPLETE", "BuildSuccess");
            publishProgress(100, "Complete!");

            long duration = System.currentTimeMillis() - startTime;
            publisher.publishPipelineCompleted(taskId, "SUCCESS", duration, 0, 6, 5);

            return MockAgentResult.success(taskId, duration, 6, 5);
        }

        private MockAgentResult simulateFault(long startTime) throws InterruptedException {
            String faultType = faultSimulation.faultType();

            if ("E02".equals(faultType)) {
                return simulateE02CompileError(startTime);
            } else if ("E05".equals(faultType)) {
                return simulateE05SecurityViolation(startTime);
            } else if ("E06".equals(faultType)) {
                return simulateE06TestFailure(startTime);
            }

            return simulateE02CompileError(startTime);
        }

        private MockAgentResult simulateE02CompileError(long startTime) throws InterruptedException {
            publisher.publishErrorDetected(taskId, "E02", "NORMAL", 
                "src/main/java/com/example/service/UserService.java", 42,
                "cannot find symbol: method finddByUid(java.lang.String)", "Did you mean: findByUid()?");
            
            publishStageChanged("BUILD_TEST", "BUG_FIX", "BuildFailed");
            publishLog("OUTER", "BUG_FIX", "Self-Healing Round 1/3", "ErrorClassifier -> E02 at line 42");
            sleep(500);

            publishLog("OUTER", "BUG_FIX", "Context collected (parallel)", "Source window + APIs + Vector Search(3 hits)");
            sleep(300);

            publishLog("OUTER", "BUG_FIX", "FixPrompt ready", "target=finddByUid -> should be findById+orElseThrow");
            publisher.publishFixAttempted(taskId, 1, "E02", "UserService.java", true);
            sleep(500);

            publishLog("FIX", "BUG_FIX", "Patch applied: line 42 fixed", "finddByUid -> findById + orElseThrow");
            publishProgress(78, "Rebuilding (Round 1)...");
            sleep(800);

            publisher.publishErrorDetected(taskId, "E02", "NORMAL",
                "UserController.java", 28,
                "variable userDto might not have been initialized", "Initialize before conditional block");
            
            publishLog("ERROR", "BUG_FIX", "Still failing after round 1", "New error introduced during fix");
            publishLog("OUTER", "BUG_FIX", "Backoff: 1s before next attempt", "");
            sleep(1000);

            publisher.publishFixAttempted(taskId, 2, "E02", "UserController.java", true);
            publishLog("FIX", "BUG_FIX", "Patch applied: UserController fixed", "Added proper initialization");
            publishProgress(85, "Rebuilding (Round 2)...");
            sleep(800);

            publishLog("SUCCESS", "BUILD_TEST", "mvn clean test BUILD SUCCESS!", "Tests run: 5, Failures: 0");
            publishStageChanged("BUG_FIX", "COMPLETE", "FixSuccess");
            publishProgress(100, "Healed!");

            long duration = System.currentTimeMillis() - startTime;
            publisher.publishPipelineCompleted(taskId, "HEALED", duration, 2, 6, 5);

            return MockAgentResult.healed(taskId, duration, 2, 6, 5);
        }

        private MockAgentResult simulateE05SecurityViolation(long startTime) throws InterruptedException {
            publisher.publishErrorDetected(taskId, "E05", "CRITICAL",
                "SecurityTestService.java", 18,
                "Path traversal detected: ../etc/passwd", "Use relative paths within generated-workspace/");
            
            publishLog("ERROR", "CODE_GENERATION", "CRITICAL: Path traversal detected!",
                "PathValidator.toRealPath(NOFOLLOW_LINKS) blocked symlink attack");
            publishLog("WARN", "CODE_GENERATION", "Forcing security fix", "CRITICAL errors get separate handling");
            sleep(300);

            publisher.publishFixAttempted(taskId, 1, "E05", "SecurityTestService.java", true);
            publishLog("FIX", "CODE_GENERATION", "Security fix applied", "Removed path traversal vulnerability");
            sleep(500);

            publishLog("SUCCESS", "BUILD_TEST", "mvn clean test BUILD SUCCESS", "Tests run: 5, Failures: 0");
            publishStageChanged("CODE_GENERATION", "COMPLETE", "BuildSuccess");
            publishProgress(100, "Complete!");

            long duration = System.currentTimeMillis() - startTime;
            publisher.publishPipelineCompleted(taskId, "HEALED", duration, 1, 6, 5);

            return MockAgentResult.healed(taskId, duration, 1, 6, 5);
        }

        private MockAgentResult simulateE06TestFailure(long startTime) throws InterruptedException {
            publisher.publishErrorDetected(taskId, "E06", "NORMAL",
                "UserServiceTest.java", 42,
                "AssertionError: expected:<test_user> but was:<null>", null);
            
            publishStageChanged("BUILD_TEST", "BUG_FIX", "BuildFailed");

            for (int round = 1; round <= 3; round++) {
                publishLog("OUTER", "BUG_FIX", "Self-Healing Round " + round + "/3", "Attempting fix...");
                publisher.publishFixAttempted(taskId, round, "E06", "UserServiceTest.java", true);
                sleep(800);
                
                if (round < 3) {
                    publishLog("ERROR", "BUG_FIX", "Fix attempt " + round + " failed", "Retrying...");
                    sleep(500);
                }
            }

            publishLog("ERROR", "BUG_FIX", "All healing attempts exhausted", "Manual intervention required");
            publishStageChanged("BUG_FIX", "MANUAL_INTERVENTION", "FixExhausted");

            publisher.publishManualInterventionRequired(taskId, "retry_exhausted",
                "Agent failed to fix E06 error after 3 rounds of self-healing.\n" +
                "Last error: AssertionError at UserServiceTest.java:42\n" +
                "Please review healing records and manually fix.");

            return MockAgentResult.manualIntervention(taskId, "E06", 3);
        }

        private void publishStageChanged(String from, String to, String transition) {
            publisher.publishStageChanged(taskId, from, to, transition);
        }

        private void publishLog(String type, String stage, String title, String summary) {
            String logId = "log-" + (++logCounter);
            String color = LOG_COLORS.getOrDefault(type, "#3b82f6");
            publisher.publishLogAdded(taskId, logId, type, stage, title, summary, color);
        }

        private void publishProgress(int percentage, String stageName) {
            publisher.publishProgressUpdated(taskId, percentage, stageName, null);
        }

        private void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }

        public void cancel() {
            this.cancelled = true;
        }
    }

    public record MockAgentResult(
        String taskId,
        String outcome,
        long durationMs,
        int healingRounds,
        int filesGenerated,
        int testsPassed,
        String errorMessage
    ) {
        public static MockAgentResult success(String taskId, long duration, int files, int tests) {
            return new MockAgentResult(taskId, "SUCCESS", duration, 0, files, tests, null);
        }

        public static MockAgentResult healed(String taskId, long duration, int rounds, int files, int tests) {
            return new MockAgentResult(taskId, "HEALED", duration, rounds, files, tests, null);
        }

        public static MockAgentResult failed(String taskId, String error) {
            return new MockAgentResult(taskId, "FAILED", 0, 0, 0, 0, error);
        }

        public static MockAgentResult cancelled(String taskId) {
            return new MockAgentResult(taskId, "CANCELLED", 0, 0, 0, 0, "User cancelled");
        }

        public static MockAgentResult manualIntervention(String taskId, String errorCategory, int rounds) {
            return new MockAgentResult(taskId, "MANUAL_INTERVENTION", 0, rounds, 0, 0, 
                "Healing exhausted for " + errorCategory);
        }
    }
}
