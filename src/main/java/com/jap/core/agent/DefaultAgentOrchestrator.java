package com.jap.core.agent;

import com.jap.api.dto.FaultSimulation;
import com.jap.api.dto.TaskOptions;
import com.jap.api.exception.ConcurrencyLimitReachedException;
import com.jap.config.JapConfigManager;
import com.jap.config.JapProperties;
import com.jap.event.TaskEventPublisher;
import com.jap.healing.ErrorClassifier;
import com.jap.llm.service.AnalysisAiService;
import com.jap.llm.service.CodeGeneratorAiService;
import com.jap.llm.service.DesignAiService;
import com.jap.llm.service.FixAiService;
import com.jap.llm.tool.CodeFileSystemTools;
import com.jap.sandbox.process.SecureMavenExecutor;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DefaultAgentOrchestrator implements AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentOrchestrator.class);

    private final ConcurrentHashMap<String, JapAgent> runningAgents = new ConcurrentHashMap<>();
    private final AtomicInteger activeCount = new AtomicInteger(0);
    private final ExecutorService agentExecutor;
    private final int maxConcurrentAgents;
    private final TaskEventPublisher eventPublisher;
    private final AnalysisAiService analysisAiService;
    private final CodeGeneratorAiService codeGeneratorAiService;
    private final DesignAiService designAiService;
    private final FixAiService fixAiService;
    private final CodeFileSystemTools fileSystemTools;
    private final SecureMavenExecutor mavenExecutor;
    private final ErrorClassifier errorClassifier;
    private final AgentPauseManager pauseManager;
    private final ChatLanguageModel chatLanguageModel;
    private final JapConfigManager configManager;

    public DefaultAgentOrchestrator(JapProperties properties, 
                                     TaskEventPublisher eventPublisher,
                                     AnalysisAiService analysisAiService,
                                     CodeGeneratorAiService codeGeneratorAiService,
                                     DesignAiService designAiService,
                                     FixAiService fixAiService,
                                     CodeFileSystemTools fileSystemTools,
                                     SecureMavenExecutor mavenExecutor,
                                     ErrorClassifier errorClassifier,
                                     AgentPauseManager pauseManager,
                                     ChatLanguageModel chatLanguageModel,
                                     JapConfigManager configManager) {
        this.maxConcurrentAgents = properties.agent().maxConcurrent();
        this.eventPublisher = eventPublisher;
        this.analysisAiService = analysisAiService;
        this.codeGeneratorAiService = codeGeneratorAiService;
        this.designAiService = designAiService;
        this.fixAiService = fixAiService;
        this.fileSystemTools = fileSystemTools;
        this.mavenExecutor = mavenExecutor;
        this.errorClassifier = errorClassifier;
        this.pauseManager = pauseManager;
        this.chatLanguageModel = chatLanguageModel;
        this.configManager = configManager;
        this.agentExecutor = createAgentExecutor(maxConcurrentAgents);
        log.info("DefaultAgentOrchestrator initialized: maxConcurrentAgents={}, self-healing enabled, two-stage approval enabled, streaming prototype enabled",
            maxConcurrentAgents);
    }

    private ExecutorService createAgentExecutor(int maxConcurrentAgents) {
        try {
            Object executor = Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
            if (executor instanceof ExecutorService executorService) {
                log.info("Using virtual-thread-per-task executor for agents");
                return executorService;
            }
        } catch (Exception e) {
            log.warn("Virtual thread executor is unavailable, fallback to fixed thread pool", e);
        }
        return Executors.newFixedThreadPool(maxConcurrentAgents);
    }

    @Override
    public CompletableFuture<JapAgent.JapAgentResult> submit(String taskId, String requirement,
                                                              TaskOptions options, 
                                                              FaultSimulation faultSimulation) {
        return submit(taskId, requirement, options, faultSimulation, JapAgent.ExecutionMode.FULL);
    }

    public CompletableFuture<JapAgent.JapAgentResult> submitDesignOnly(String taskId, String requirement,
                                                                         TaskOptions options, 
                                                                         FaultSimulation faultSimulation) {
        return submit(taskId, requirement, options, faultSimulation, JapAgent.ExecutionMode.DESIGN_ONLY);
    }

    private CompletableFuture<JapAgent.JapAgentResult> submit(String taskId, String requirement,
                                                                TaskOptions options, 
                                                                FaultSimulation faultSimulation,
                                                                JapAgent.ExecutionMode mode) {
        if (activeCount.get() >= maxConcurrentAgents) {
            throw new ConcurrencyLimitReachedException(maxConcurrentAgents);
        }

        CompletableFuture<JapAgent.JapAgentResult> future = new CompletableFuture<>();

        agentExecutor.submit(() -> {
            activeCount.incrementAndGet();
            try {
                log.info("[{}] JapAgent started (active={}/{}, mode={})", 
                    taskId, activeCount.get(), maxConcurrentAgents, mode);

                JapAgent agent = new JapAgent(taskId, requirement, options, faultSimulation, 
                                              eventPublisher, analysisAiService,
                                              codeGeneratorAiService, designAiService, fixAiService,
                                              fileSystemTools, mavenExecutor, errorClassifier, pauseManager,
                                              chatLanguageModel, configManager);
                agent.setExecutionMode(mode);
                runningAgents.put(taskId, agent);

                JapAgent.JapAgentResult result = agent.run(mode);
                future.complete(result);

                return result;
            } catch (Exception e) {
                log.error("[{}] JapAgent failed unexpectedly", taskId, e);
                JapAgent.JapAgentResult failure = JapAgent.JapAgentResult.failed(taskId, e.getMessage());
                future.complete(failure);
                return failure;
            } finally {
                activeCount.decrementAndGet();
                runningAgents.remove(taskId);
                log.info("[{}] JapAgent finished (active={})", taskId, activeCount.get());
            }
        });

        return future;
    }

    public boolean resume(String taskId) {
        boolean resumed = pauseManager.resume(taskId);
        if (resumed) {
            log.info("[{}] JapAgent resumed from WAITING_FOR_APPROVAL", taskId);
        }
        return resumed;
    }

    @Override
    public boolean cancel(String taskId) {
        JapAgent agent = runningAgents.get(taskId);
        if (agent != null) {
            agent.cancel();
            log.info("[{}] JapAgent cancellation requested", taskId);
            return true;
        }
        return false;
    }

    @Override
    public int getActiveCount() {
        return activeCount.get();
    }
}
