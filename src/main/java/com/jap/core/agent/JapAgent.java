package com.jap.core.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jap.api.dto.FaultSimulation;
import com.jap.api.dto.TaskOptions;
import com.jap.api.ws.*;
import com.jap.config.JapProperties;
import com.jap.core.state.AgentStatus;
import com.jap.event.TaskEventPublisher;
import com.jap.healing.ErrorClassifier;
import com.jap.healing.ErrorClassifier.ClassifiedError;
import com.jap.healing.ErrorClassifier.ClassifiedErrors;
import com.jap.llm.dto.CodeGenerationResult;
import com.jap.llm.dto.CodePatch;
import com.jap.llm.dto.CodePatchResult;
import com.jap.llm.dto.GeneratedFile;
import com.jap.llm.dto.RequirementSpec;
import com.jap.llm.exception.E01FormatException;
import com.jap.llm.service.AnalysisAiService;
import com.jap.llm.service.CodeGeneratorAiService;
import com.jap.llm.service.DesignAiService;
import com.jap.llm.service.FixAiService;
import com.jap.llm.tool.CodeFileSystemTools;
import com.jap.sandbox.exception.SandboxViolationException;
import com.jap.sandbox.process.BuildResult;
import com.jap.sandbox.process.SecureMavenExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JapAgent {

    private static final Logger log = LoggerFactory.getLogger(JapAgent.class);

    private static final int MAX_E01_RETRIES = 3;
    private static final int MAX_E02_RETRIES = 3;
    private static final String JSON_REPAIR_HINT =
        "上一次的输出 JSON 格式有误（未闭合），请重新生成一份简洁且结构完整的 JSON。";
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

    public enum ExecutionMode {
        DESIGN_ONLY,
        FULL
    }

    private final String taskId;
    private final String requirement;
    private final String analysisPromptOverride;
    private final TaskOptions options;
    private final FaultSimulation faultSimulation;
    private final TaskEventPublisher publisher;
    private final AnalysisAiService analysisAiService;
    private final CodeGeneratorAiService codeGeneratorAiService;
    private final DesignAiService designAiService;
    private final FixAiService fixAiService;
    private final CodeFileSystemTools fileSystemTools;
    private final SecureMavenExecutor mavenExecutor;
    private final ErrorClassifier errorClassifier;
    private final ObjectMapper objectMapper;
    private final AgentPauseManager pauseManager;
    private final dev.langchain4j.model.chat.ChatLanguageModel chatLanguageModel;
    private final com.jap.config.JapConfigManager configManager;
    private final String taskWorkspaceDir;

    private volatile boolean cancelled = false;
    private int logCounter = 0;
    private AgentStatus currentStatus = AgentStatus.IDLE;
    private RequirementSpec requirementSpec;
    private final List<String> generatedFilePaths = new ArrayList<>();
    private int totalHealingRounds = 0;
    private ExecutionMode executionMode = ExecutionMode.FULL;
    private volatile String currentAction = "";
    private volatile int currentProgress = 0;
    private Thread heartbeatThread;

    public JapAgent(String taskId, String requirement, String analysisPromptOverride, TaskOptions options,
                    FaultSimulation faultSimulation, TaskEventPublisher publisher,
                    AnalysisAiService analysisAiService,
                    CodeGeneratorAiService codeGeneratorAiService,
                    DesignAiService designAiService,
                    FixAiService fixAiService,
                    CodeFileSystemTools fileSystemTools,
                    SecureMavenExecutor mavenExecutor,
                    ErrorClassifier errorClassifier,
                    AgentPauseManager pauseManager,
                    dev.langchain4j.model.chat.ChatLanguageModel chatLanguageModel,
                    com.jap.config.JapConfigManager configManager) {
        this.taskId = taskId;
        this.requirement = requirement;
        this.analysisPromptOverride = analysisPromptOverride;
        this.options = options;
        this.faultSimulation = faultSimulation;
        this.publisher = publisher;
        this.analysisAiService = analysisAiService;
        this.codeGeneratorAiService = codeGeneratorAiService;
        this.designAiService = designAiService;
        this.fixAiService = fixAiService;
        this.fileSystemTools = fileSystemTools;
        this.mavenExecutor = mavenExecutor;
        this.errorClassifier = errorClassifier;
        this.objectMapper = new ObjectMapper();
        this.pauseManager = pauseManager;
        this.chatLanguageModel = chatLanguageModel;
        this.configManager = configManager;
        this.taskWorkspaceDir = "tasks/" + taskId;
    }

    public void setExecutionMode(ExecutionMode mode) {
        this.executionMode = mode;
    }

    public JapAgentResult run() {
        return run(ExecutionMode.FULL);
    }

    public JapAgentResult run(ExecutionMode mode) {
        this.executionMode = mode;
        long startTime = System.currentTimeMillis();
        
        ensureWorkspaceExists();
        configManager.activateTaskLlmContext(taskId);
        
        startHeartbeat();

        try {
            publishStageChanged("IDLE", "INTENT_ANALYSIS", "StartAnalysis");
            publishLog("SYSTEM", null, "Agent Pipeline launched", taskId);
            currentAction = "分析需求中...";
            sleep(200);

            if (!performIntentAnalysis()) {
                stopHeartbeat();
                return JapAgentResult.failed(taskId, "Intent analysis failed after retries");
            }

            if (cancelled) {
                stopHeartbeat();
                return JapAgentResult.cancelled(taskId);
            }

            publishStageChanged("INTENT_ANALYSIS", "DESIGN", "AnalysisComplete");
            publishProgress(25, "Generating design documents...");
            currentAction = "生成设计文档中...";
            sleep(300);

            if (cancelled) {
                stopHeartbeat();
                return JapAgentResult.cancelled(taskId);
            }

            int designFilesGenerated = performDesignGeneration();

            if (cancelled) {
                stopHeartbeat();
                return JapAgentResult.cancelled(taskId);
            }

            if (executionMode == ExecutionMode.DESIGN_ONLY) {
                return waitForApprovalAndContinue(designFilesGenerated, startTime);
            }

            return continueWithImplementation(designFilesGenerated, startTime);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopHeartbeat();
            return JapAgentResult.cancelled(taskId);
        } catch (Exception e) {
            log.error("[{}] Agent execution failed", taskId, e);
            publishLog("ERROR", currentStatus.name(), "Agent failed", e.getMessage());
            stopHeartbeat();
            return JapAgentResult.failed(taskId, e.getMessage());
        } finally {
            configManager.clearThreadLlmContext();
        }
    }

    private JapAgentResult waitForApprovalAndContinue(int designFilesGenerated, long startTime) throws InterruptedException {
        publishStageChanged("DESIGN", "WAITING_FOR_APPROVAL", "DesignComplete");
        publishProgress(35, "Waiting for approval...");
        publishLog("SYSTEM", "WAITING_FOR_APPROVAL", "Design phase complete - waiting for approval",
            "Generated " + designFilesGenerated + " design documents. Click 'Approve & Continue' to proceed.");
        
        publisher.publishDesignComplete(taskId, designFilesGenerated, generatedFilePaths);

        AgentPauseManager.PausePoint pausePoint = pauseManager.createPausePoint(taskId);
        
        boolean resumed = pausePoint.await(30 * 60 * 1000);
        
        if (pausePoint.isCancelled() || cancelled) {
            publishLog("SYSTEM", "WAITING_FOR_APPROVAL", "Task cancelled", "User cancelled the task");
            publishStageChanged("WAITING_FOR_APPROVAL", "CANCELLED", "UserCancelled");
            return JapAgentResult.cancelled(taskId);
        }
        
        if (!resumed) {
            publishLog("ERROR", "WAITING_FOR_APPROVAL", "Approval timeout", "No approval received within 30 minutes");
            publishStageChanged("WAITING_FOR_APPROVAL", "FAILED", "Timeout");
            return JapAgentResult.failed(taskId, "Approval timeout");
        }
        
        publishLog("SUCCESS", "WAITING_FOR_APPROVAL", "Approval received", "Continuing with implementation...");
        pauseManager.removePausePoint(taskId);
        
        return continueWithImplementation(designFilesGenerated, startTime);
    }

    private JapAgentResult continueWithImplementation(int designFilesGenerated, long startTime) throws InterruptedException {
        publishStageChanged("DESIGN", "CODE_GENERATION", "DesignComplete");
        publishProgress(35, "Design documents generated: " + designFilesGenerated + " files");
        sleep(300);

        if (cancelled) {
            return JapAgentResult.cancelled(taskId);
        }

        int filesGenerated = performCodeGeneration();
        if (filesGenerated == 0) {
            publishLog("WARN", "CODE_GENERATION", "No files generated", "Code generation returned empty result");
        }

        if (cancelled) {
            return JapAgentResult.cancelled(taskId);
        }

        publishStageChanged("CODE_GENERATION", "BUILD_TEST", "GenerationComplete");
        publishProgress(72, "Code generation complete: " + filesGenerated + " files");
        sleep(300);

        if (cancelled) {
            return JapAgentResult.cancelled(taskId);
        }

        BuildTestResult buildResult = performBuildTest();

        if (buildResult.success()) {
            publishLog("SUCCESS", "BUILD_TEST", "Build successful", 
                "All files compiled without errors");
            publishStageChanged("BUILD_TEST", "COMPLETE", "BuildSuccess");
            publishProgress(100, "Complete!");
        } else {
            publishLog("ERROR", "BUILD_TEST", "Build failed after healing", 
                buildResult.errorMessage());
            publishStageChanged("BUILD_TEST", "COMPLETE", "BuildFailed");
        }

        long duration = System.currentTimeMillis() - startTime;
        
        String outcome = buildResult.success() ? 
            (totalHealingRounds > 0 ? "HEALED" : "SUCCESS") : 
            "MANUAL_INTERVENTION";
        
        publisher.publishPipelineCompleted(taskId, outcome, duration, 
            totalHealingRounds, filesGenerated, 0);

        if (buildResult.success()) {
            return totalHealingRounds > 0 
                ? JapAgentResult.healed(taskId, duration, totalHealingRounds, filesGenerated, 0)
                : JapAgentResult.success(taskId, duration, filesGenerated, 0);
        } else {
            return JapAgentResult.manualIntervention(taskId, "E02", totalHealingRounds);
        }
    }

    private boolean performIntentAnalysis() throws InterruptedException {
        publishLog("INFO", "INTENT_ANALYSIS", "INTENT_ANALYSIS started", "Analyzing requirement...");
        publishProgress(5, "Analyzing requirement...");
        sleep(500);

        String packageName = options != null && options.packageName() != null 
            ? options.packageName() 
            : "com.example.generated";
        String databaseType = options != null && options.databaseType() != null 
            ? options.databaseType() 
            : "MYSQL";

        int retryCount = 0;
        String lastError = null;
        String lastRawOutput = null;

        while (retryCount < MAX_E01_RETRIES) {
            if (cancelled) {
                return false;
            }

            try {
                publishLog("INNER", "INTENT_ANALYSIS", "Sending to AnalysisAiService", 
                    "JSON Schema constraint applied (attempt " + (retryCount + 1) + "/" + MAX_E01_RETRIES + ")");
                
                RequirementSpec spec;
                boolean hasRawOutput = lastRawOutput != null && !lastRawOutput.isBlank();
                boolean hasPromptOverride = analysisPromptOverride != null && !analysisPromptOverride.isBlank();
                if (retryCount == 0 || !hasRawOutput) {
                    if (hasPromptOverride) {
                        spec = analysisAiService.analyzeRequirementFromPrompt(analysisPromptOverride);
                    } else {
                        spec = analysisAiService.analyzeRequirement(requirement, packageName, databaseType);
                    }
                } else {
                    publishLog("INNER", "INTENT_ANALYSIS", "E01 Self-Correction", 
                        "Retrying with error context: " + lastError);
                    if (hasPromptOverride) {
                        spec = analysisAiService.correctAnalysisFromPrompt(analysisPromptOverride, lastError, lastRawOutput);
                    } else {
                        spec = analysisAiService.correctAnalysis(requirement, lastError, lastRawOutput);
                    }
                }

                validateRequirementSpec(spec);
                this.requirementSpec = spec;

                String summary = spec.summary() != null ? spec.summary() : "Requirement parsed";
                int moduleCount = spec.modules() != null ? spec.modules().size() : 0;
                publishLog("INNER", "INTENT_ANALYSIS", "RequirementSpec parsed successfully", 
                    summary + " (" + moduleCount + " modules resolved)");
                
                log.info("[{}] Intent analysis completed: {} modules", taskId, moduleCount);
                return true;

            } catch (E01FormatException e) {
                retryCount++;
                lastError = e.getMessage();
                if (isMalformedJsonError(e.getMessage())) {
                    lastError = (lastError == null ? "" : lastError + " ") + JSON_REPAIR_HINT;
                }
                lastRawOutput = e.getRawOutput();
                if (lastRawOutput == null || lastRawOutput.isBlank()) {
                    lastRawOutput = "{}";
                }

                log.warn("[{}] E01 format error (attempt {}/{}): {}", 
                    taskId, retryCount, MAX_E01_RETRIES, e.getMessage());
                
                publishLog("ERROR", "INTENT_ANALYSIS", "LLM returned malformed output", 
                    "E01 format error: " + e.getMessage());

                if (retryCount < MAX_E01_RETRIES) {
                    publishLog("INNER", "INTENT_ANALYSIS", "Self-Correction triggered", 
                        "Preparing retry with error context...");
                    sleep(500);
                }
            } catch (Exception e) {
                log.error("[{}] Unexpected error during intent analysis", taskId, e);
                publishLog("ERROR", "INTENT_ANALYSIS", "Analysis failed", e.getMessage());
                
                if (e.getCause() != null && e.getCause().getMessage() != null) {
                    lastError = e.getCause().getMessage();
                } else {
                    lastError = e.getMessage();
                }
                if (isMalformedJsonError(lastError)) {
                    lastError = (lastError == null ? "" : lastError + " ") + JSON_REPAIR_HINT;
                    lastRawOutput = "{}";
                }
                if (lastError == null || lastError.isBlank()) {
                    lastError = e.getClass().getSimpleName();
                }
                if (lastRawOutput == null) {
                    lastRawOutput = "";
                }
                retryCount++;
                
                if (retryCount < MAX_E01_RETRIES) {
                    sleep(1000);
                }
            }
        }

        publishLog("ERROR", "INTENT_ANALYSIS", "E01 Self-Correction exhausted", 
            "Failed after " + MAX_E01_RETRIES + " attempts");
        return false;
    }

    private int performDesignGeneration() throws InterruptedException {
        publishLog("INFO", "DESIGN", "DESIGN phase started", "Generating design documents...");
        
        if (requirementSpec == null) {
            publishLog("WARN", "DESIGN", "No requirement spec", "Skipping design generation");
            return 0;
        }

        String packageName = requirementSpec.packageName();
        String databaseType = requirementSpec.databaseType() != null ? requirementSpec.databaseType() : "H2";
        int generatedCount = 0;

        try {
            fileSystemTools.createDirectory(taskPath("docs"));
            fileSystemTools.createDirectory(taskPath("prototype"));
        } catch (SandboxViolationException e) {
            log.warn("[{}] Failed to create design directories", taskId, e);
        }

        // Step A: Generate PRD
        currentAction = "生成 PRD 文档中...";
        publishLog("INNER", "DESIGN", "Generating PRD document", "Product Requirements Document");
        publishProgress(27, "正在生成 PRD...");

        try {
            String prd = designAiService.generatePRD(requirement, packageName, databaseType);
            if (prd != null && !prd.isBlank()) {
                String prdPath = taskPath("docs/1_PRD.md");
                fileSystemTools.writeFile(prdPath, prd, true);
                generatedFilePaths.add(prdPath);
                publisher.publishFileGenerated(taskId, prdPath, prd.getBytes().length, "MARKDOWN");
                publishLog("SUCCESS", "DESIGN", "PRD generated", prdPath);
                generatedCount++;
            }
        } catch (Exception e) {
            log.error("[{}] Failed to generate PRD", taskId, e);
            publishLog("ERROR", "DESIGN", "PRD generation failed", e.getMessage());
        }

        if (cancelled) return generatedCount;

        // Step B: Generate Architecture
        currentAction = "生成架构文档中...";
        publishLog("INNER", "DESIGN", "Generating Architecture document", "System Architecture Design");
        publishProgress(30, "正在生成架构设计...");

        try {
            String prdSummary = requirementSpec.summary() != null ? requirementSpec.summary() : requirement;
            String architecture = designAiService.generateArchitecture(requirement, prdSummary, packageName);
            if (architecture != null && !architecture.isBlank()) {
                String architecturePath = taskPath("docs/2_ARCHITECTURE.md");
                fileSystemTools.writeFile(architecturePath, architecture, true);
                generatedFilePaths.add(architecturePath);
                publisher.publishFileGenerated(taskId, architecturePath, architecture.getBytes().length, "MARKDOWN");
                publishLog("SUCCESS", "DESIGN", "Architecture generated", architecturePath);
                generatedCount++;
            }
        } catch (Exception e) {
            log.error("[{}] Failed to generate Architecture", taskId, e);
            publishLog("ERROR", "DESIGN", "Architecture generation failed", e.getMessage());
        }

        if (cancelled) return generatedCount;

        // Step C: Generate HTML Prototype (with streaming and progress)
        currentAction = "生成 HTML 原型中...";
        publishLog("INNER", "DESIGN", "Generating HTML prototype", "Visual Prototype (streaming mode)");
        publishProgress(33, "正在生成 HTML 原型...");
        
        try {
            String apiEndpoints = extractApiEndpoints(requirementSpec);
            String prototypePrompt = buildPrototypePrompt(requirement, apiEndpoints, packageName);
            
            com.jap.llm.streaming.StreamingHtmlGenerator generator = 
                new com.jap.llm.streaming.StreamingHtmlGenerator(
                    configManager.getStreamingChatLanguageModel(),
                    publisher,
                    fileSystemTools,
                    configManager,
                    taskId
                );
            
            com.jap.llm.streaming.StreamingHtmlGenerator.StreamingResult result = 
                generator.generateWithStreaming(prototypePrompt, taskPath("prototype/index.html"));
            
            if (result.success() || result.isPartial()) {
                String prototypePath = taskPath("prototype/index.html");
                generatedFilePaths.add(prototypePath);
                publisher.publishFileGenerated(taskId, prototypePath, result.charsWritten(), "HTML");
                
                if (result.success()) {
                    publishLog("SUCCESS", "DESIGN", "Prototype generated", 
                        prototypePath + " (" + result.charsWritten() + " chars)");
                    generatedCount++;
                } else {
                    publishLog("WARN", "DESIGN", "Prototype partially generated", 
                        prototypePath + " (" + result.charsWritten() + " chars, incomplete)");
                    generatedCount++;
                }
            } else if (result.error() != null) {
                log.error("[{}] Failed to generate Prototype: {}", taskId, result.error().getMessage());
                publishLog("ERROR", "DESIGN", "Prototype generation failed", result.error().getMessage());
            }
        } catch (Exception e) {
            log.error("[{}] Failed to generate Prototype", taskId, e);
            publishLog("ERROR", "DESIGN", "Prototype generation failed", e.getMessage());
        }

        currentAction = "设计阶段完成";
        publishLog("SUCCESS", "DESIGN", "Design phase complete", 
            "Generated " + generatedCount + " design documents");
        
        return generatedCount;
    }

    private String extractApiEndpoints(RequirementSpec spec) {
        if (spec == null) return "";
        
        StringBuilder sb = new StringBuilder();
        
        if (spec.apiEndpoints() != null) {
            for (RequirementSpec.ApiEndpoint endpoint : spec.apiEndpoints()) {
                sb.append(endpoint.method()).append(" ").append(endpoint.path())
                  .append(" - ").append(endpoint.description())
                  .append("\n");
            }
        }
        
        if (spec.modules() != null) {
            for (RequirementSpec.ModuleSpec module : spec.modules()) {
                if (module.type() != null && module.type().contains("CONTROLLER")) {
                    sb.append("Controller: ").append(module.name()).append("\n");
                    if (module.methods() != null) {
                        for (RequirementSpec.MethodSpec method : module.methods()) {
                            if (method.httpMethod() != null && method.path() != null) {
                                sb.append("  ").append(method.httpMethod())
                                  .append(" ").append(method.path())
                                  .append(" - ").append(method.name())
                                  .append("\n");
                            }
                        }
                    }
                }
            }
        }
        
        return sb.toString();
    }

    private String buildPrototypePrompt(String requirement, String apiEndpoints, String packageName) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请为以下系统生成一个功能性 HTML 原型。\n\n");
        prompt.append("## 用户需求\n").append(requirement).append("\n\n");
        prompt.append("## API 端点\n").append(apiEndpoints).append("\n\n");
        prompt.append("## 包名\n").append(packageName).append("\n\n");
        prompt.append("请生成一个完整的 HTML 文件，包含：\n");
        prompt.append("1. 头部 - 应用标题和导航\n");
        prompt.append("2. 主内容区 - 核心功能的表单和列表\n");
        prompt.append("3. JavaScript - 模拟数据处理和交互\n");
        prompt.append("使用 Tailwind CSS CDN 进行样式设计。\n");
        prompt.append("直接输出 HTML 代码，从 <!DOCTYPE html> 开始，不要包含 markdown 标记。");
        return prompt.toString();
    }

    private int performCodeGeneration() throws InterruptedException {
        publishLog("INFO", "CODE_GENERATION", "CODE_GENERATION started", "Generating code from specification...");
        
        if (requirementSpec == null || requirementSpec.modules() == null || requirementSpec.modules().isEmpty()) {
            publishLog("WARN", "CODE_GENERATION", "No modules to generate", "RequirementSpec has no modules");
            return 0;
        }

        String packageName = requirementSpec.packageName();
        String packagePath = packageName.replace('.', '/');
        String databaseType = requirementSpec.databaseType() != null ? requirementSpec.databaseType() : "H2";

        publishLog("INNER", "CODE_GENERATION", "Initializing project structure", 
            "Creating directories for " + packageName);
        
        try {
            initializeProjectStructure(packagePath);
        } catch (SandboxViolationException e) {
            log.error("[{}] Failed to initialize project structure", taskId, e);
            publishLog("ERROR", "CODE_GENERATION", "Directory initialization failed", e.getMessage());
            return 0;
        }

        List<RequirementSpec.ModuleSpec> modules = requirementSpec.modules();
        int totalModules = modules.size();
        int generatedCount = 0;
        StringBuilder previousFiles = new StringBuilder();

        publishProgress(20, "Generating " + totalModules + " modules...");

        for (int i = 0; i < modules.size(); i++) {
            if (cancelled) {
                return generatedCount;
            }

            RequirementSpec.ModuleSpec module = modules.get(i);
            String moduleName = module.name();
            String moduleType = module.type();

            int progressPercent = 20 + (int) ((i + 1.0) / totalModules * 50);
            publishProgress(progressPercent, "Generating " + moduleName + " (" + moduleType + ")...");

            publishLog("INNER", "CODE_GENERATION", "Generating module: " + moduleName, 
                "Type: " + moduleType + " (" + (i + 1) + "/" + totalModules + ")");

            try {
                String specJson = toJsonSafe(requirementSpec);
                String description = module.description() != null ? module.description() : "";
                
                CodeGenerationResult result = codeGeneratorAiService.generateModule(
                    specJson, moduleName, moduleType, description, packageName, previousFiles.toString()
                );

                if (result != null && result.files() != null) {
                    for (GeneratedFile file : result.files()) {
                        if (file.path() != null && file.content() != null) {
                            boolean written = writeGeneratedFile(file);
                            if (written) {
                                generatedCount++;
                                previousFiles.append("--- FILE: ").append(file.path()).append(" ---\n");
                                previousFiles.append(file.content()).append("\n\n");
                            }
                        }
                    }
                }

                sleep(100);

            } catch (Exception e) {
                log.error("[{}] Failed to generate module: {}", taskId, moduleName, e);
                publishLog("ERROR", "CODE_GENERATION", "Module generation failed: " + moduleName, 
                    e.getMessage());
            }
        }

        publishLog("SUCCESS", "CODE_GENERATION", "Code generation complete", 
            "Generated " + generatedCount + " files from " + totalModules + " modules");
        
        return generatedCount;
    }

    private BuildTestResult performBuildTest() throws InterruptedException {
        publishLog("INFO", "BUILD_TEST", "BUILD_TEST started", "Executing Maven compile...");
        publishProgress(75, "Compiling generated code...");

        Path projectPath = Path.of(taskWorkspaceDir);
        
        BuildResult initialBuild = mavenExecutor.executeCompile(projectPath);
        
        if (initialBuild.success()) {
            log.info("[{}] Initial build successful", taskId);
            return BuildTestResult.ok();
        }

        log.warn("[{}] Initial build failed with {} errors", taskId, initialBuild.getErrorCount());
        publishLog("OUTER", "BUILD_TEST", "Build failed - starting self-healing", 
            "Found " + initialBuild.getErrorCount() + " compilation errors");

        ClassifiedErrors classifiedErrors = errorClassifier.classify(initialBuild);
        
        for (ClassifiedError error : classifiedErrors.errors()) {
            publisher.publishErrorDetected(
                taskId,
                error.category().getCode(),
                error.severity().name(),
                error.filePath(),
                error.lineNumber(),
                error.message(),
                error.category().getDescription()
            );
            
            publishLog("ERROR", "BUILD_TEST", "Error detected: " + error.category().getCode(),
                error.filePath() + ":" + error.lineNumber() + " - " + error.message());
        }

        int healingRound = 0;
        List<String> previousAttempts = new ArrayList<>();

        while (healingRound < MAX_E02_RETRIES) {
            if (cancelled) {
                return BuildTestResult.cancelled();
            }

            healingRound++;
            totalHealingRounds = healingRound;

            publishLog("OUTER", "BUILD_TEST", "E02 Self-Healing Round " + healingRound + "/" + MAX_E02_RETRIES,
                "Analyzing " + classifiedErrors.getErrorCount() + " errors");
            publishProgress(75 + healingRound * 5, "Self-healing round " + healingRound + "...");

            String originalFiles = collectOriginalFiles();
            String errorContext = classifiedErrors.getFormattedSummary();
            String previousAttemptsStr = String.join("\n---\n", previousAttempts);

            try {
                CodePatchResult patchResult = fixAiService.generatePatches(
                    originalFiles,
                    errorContext,
                    buildDetailedErrorContext(classifiedErrors),
                    previousAttemptsStr
                );

                if (!patchResult.success() || patchResult.patches().isEmpty()) {
                    log.warn("[{}] FixAiService returned no patches", taskId);
                    previousAttempts.add("Round " + healingRound + ": No patches generated");
                    continue;
                }

                boolean anyPatchApplied = false;
                for (CodePatch patch : patchResult.patches()) {
                    boolean applied = applyPatch(patch, healingRound);
                    if (applied) {
                        anyPatchApplied = true;
                    }
                }

                if (!anyPatchApplied) {
                    log.warn("[{}] No patches could be applied", taskId);
                    previousAttempts.add("Round " + healingRound + ": Patches could not be applied");
                    continue;
                }

                previousAttempts.add("Round " + healingRound + ": " + patchResult.summary());

                sleep(500);

                BuildResult retryBuild = mavenExecutor.executeCompile(projectPath);

                if (retryBuild.success()) {
                    log.info("[{}] Build successful after {} healing rounds", taskId, healingRound);
                    publishLog("SUCCESS", "BUILD_TEST", "Self-healing successful!",
                        "Fixed all errors in " + healingRound + " round(s)");
                    return BuildTestResult.healed(healingRound);
                }

                classifiedErrors = errorClassifier.classify(retryBuild);
                
                publishLog("OUTER", "BUILD_TEST", "Build still failing after round " + healingRound,
                    "Remaining errors: " + classifiedErrors.getErrorCount());

            } catch (Exception e) {
                log.error("[{}] Error during healing round {}", taskId, healingRound, e);
                publishLog("ERROR", "BUILD_TEST", "Healing round " + healingRound + " failed",
                    e.getMessage());
                previousAttempts.add("Round " + healingRound + ": Exception - " + e.getMessage());
            }
        }

        log.error("[{}] E02 Self-Healing exhausted after {} rounds", taskId, MAX_E02_RETRIES);
        publishLog("ERROR", "BUILD_TEST", "Self-healing exhausted",
            "Failed after " + MAX_E02_RETRIES + " rounds - manual intervention required");
        
        publisher.publishManualInterventionRequired(taskId, 
            "E02_COMPILATION_ERROR", 
            "Could not fix compilation errors after " + MAX_E02_RETRIES + " attempts");

        return BuildTestResult.exhausted(MAX_E02_RETRIES, classifiedErrors.getFormattedSummary());
    }

    private String collectOriginalFiles() {
        StringBuilder sb = new StringBuilder();
        for (String filePath : generatedFilePaths) {
            try {
                String content = fileSystemTools.readFile(filePath);
                sb.append("--- FILE: ").append(filePath).append(" ---\n");
                sb.append(content).append("\n\n");
            } catch (Exception e) {
                log.warn("[{}] Could not read file: {}", taskId, filePath);
            }
        }
        return sb.toString();
    }

    private String buildDetailedErrorContext(ClassifiedErrors errors) {
        return errors.errors().stream()
            .map(ClassifiedError::toPromptContext)
            .collect(Collectors.joining("\n---\n"));
    }

    private boolean applyPatch(CodePatch patch, int round) {
        try {
            String filePath = patch.filePath();
            String patchedContent = patch.patchedContent();
            String taskScopedPath = taskPath(filePath);

            fileSystemTools.writeFile(taskScopedPath, patchedContent, true);

            publisher.publishFixAttempted(taskId, round, "E02", taskScopedPath, true);
            publishLog("FIX", "BUILD_TEST", "Patch applied: " + taskScopedPath,
                patch.description() + " (round " + round + ")");

            log.info("[{}] Applied patch to: {}", taskId, taskScopedPath);
            return true;

        } catch (SandboxViolationException e) {
            log.error("[{}] Failed to apply patch: {}", taskId, patch.filePath(), e);
            publishLog("ERROR", "BUILD_TEST", "Failed to apply patch: " + patch.filePath(),
                e.getMessage());
            return false;
        }
    }

    private void initializeProjectStructure(String packagePath) throws SandboxViolationException {
        String[] directories = {
            taskPath("src/main/java/" + packagePath),
            taskPath("src/main/java/" + packagePath + "/entity"),
            taskPath("src/main/java/" + packagePath + "/repository"),
            taskPath("src/main/java/" + packagePath + "/service"),
            taskPath("src/main/java/" + packagePath + "/controller"),
            taskPath("src/main/java/" + packagePath + "/dto"),
            taskPath("src/main/java/" + packagePath + "/config"),
            taskPath("src/main/resources"),
            taskPath("src/test/java/" + packagePath)
        };

        for (String dir : directories) {
            try {
                fileSystemTools.createDirectory(dir);
                log.debug("[{}] Created directory: {}", taskId, dir);
            } catch (SandboxViolationException e) {
                log.debug("[{}] Directory already exists or created: {}", taskId, dir);
            }
        }
    }

    private boolean writeGeneratedFile(GeneratedFile file) {
        try {
            String path = file.path();
            String content = file.content();
            String taskScopedPath = taskPath(path);
            
            fileSystemTools.writeFile(taskScopedPath, content, true);
            generatedFilePaths.add(taskScopedPath);
            
            String language = file.getLanguage();
            long size = content.getBytes().length;
            
            publisher.publishFileGenerated(taskId, taskScopedPath, size, language);
            
            log.info("[{}] Generated file: {} ({} bytes)", taskId, taskScopedPath, size);
            publishLog("SUCCESS", "CODE_GENERATION", "File created: " + taskScopedPath, 
                language + " - " + size + " bytes");
            
            return true;

        } catch (SandboxViolationException e) {
            log.error("[{}] Failed to write file: {}", taskId, file.path(), e);
            publishLog("ERROR", "CODE_GENERATION", "Failed to write: " + file.path(), e.getMessage());
            return false;
        }
    }

    private void validateRequirementSpec(RequirementSpec spec) throws E01FormatException {
        if (spec == null) {
            throw new E01FormatException("RequirementSpec is null", "null");
        }

        if (spec.summary() == null || spec.summary().isBlank()) {
            throw new E01FormatException("Missing required field: summary", toJsonSafe(spec));
        }

        if (spec.packageName() == null || spec.packageName().isBlank()) {
            throw new E01FormatException("Missing required field: packageName", toJsonSafe(spec));
        }

        if (spec.modules() == null || spec.modules().isEmpty()) {
            throw new E01FormatException("Missing or empty required field: modules", toJsonSafe(spec));
        }

        for (RequirementSpec.ModuleSpec module : spec.modules()) {
            if (module.name() == null || module.name().isBlank()) {
                throw new E01FormatException("Module missing required field: name", toJsonSafe(spec));
            }
            if (module.type() == null || module.type().isBlank()) {
                throw new E01FormatException("Module '" + module.name() + "' missing required field: type", 
                    toJsonSafe(spec));
            }
        }

        log.debug("[{}] RequirementSpec validation passed", taskId);
    }

    private boolean isMalformedJsonError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("malformedjsonexception")
            || lower.contains("malformed json")
            || lower.contains("json parse")
            || lower.contains("unexpected end of input")
            || lower.contains("unexpected end-of-input")
            || lower.contains("unclosed");
    }

    private String toJsonSafe(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return String.valueOf(obj);
        }
    }

    private void publishStageChanged(String from, String to, String transition) {
        currentStatus = AgentStatus.valueOf(to);
        publisher.publishStageChanged(taskId, from, to, transition);
    }

    private void publishLog(String type, String stage, String title, String summary) {
        String logId = "log-" + (++logCounter);
        String color = LOG_COLORS.getOrDefault(type, "#3b82f6");
        publisher.publishLogAdded(taskId, logId, type, stage, title, summary, color);
    }

    private void publishProgress(int percentage, String stageName) {
        publisher.publishProgressUpdated(taskId, percentage, stageName, null);
        this.currentProgress = percentage;
        this.currentAction = stageName;
    }

    private void sleep(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }
    
    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (!cancelled && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(2000);
                    if (!cancelled) {
                        publisher.publish(taskId, new com.jap.api.ws.StompProgressHeartbeat(
                            currentStatus.name(),
                            0,
                            currentAction,
                            currentProgress
                        ));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "heartbeat-" + taskId);
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }
    
    private void stopHeartbeat() {
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
            heartbeatThread = null;
        }
    }
    
    private void ensureWorkspaceExists() {
        try {
            if (configManager != null) {
                java.nio.file.Path workspacePath = configManager.getWorkspacePath();
                if (!java.nio.file.Files.exists(workspacePath)) {
                    java.nio.file.Files.createDirectories(workspacePath);
                    log.info("[{}] Created workspace directory: {}", taskId, workspacePath);
                }
                java.nio.file.Path taskWorkspacePath = workspacePath.resolve(taskWorkspaceDir).normalize();
                if (!java.nio.file.Files.exists(taskWorkspacePath)) {
                    java.nio.file.Files.createDirectories(taskWorkspacePath);
                    log.info("[{}] Created task workspace directory: {}", taskId, taskWorkspacePath);
                }
                publishLog("INFO", "INIT", "Workspace ready", taskWorkspacePath.toString());
            }
        } catch (Exception e) {
            log.error("[{}] Failed to ensure workspace exists: {}", taskId, e.getMessage());
            publishLog("ERROR", "INIT", "Failed to create workspace", e.getMessage());
        }
    }

    private String taskPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return taskWorkspaceDir;
        }
        String normalized = relativePath.replace("\\", "/");
        if (normalized.startsWith(taskWorkspaceDir + "/") || normalized.equals(taskWorkspaceDir)) {
            return normalized;
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return taskWorkspaceDir + "/" + normalized;
    }

    public void cancel() {
        this.cancelled = true;
        stopHeartbeat();
    }

    public RequirementSpec getRequirementSpec() {
        return requirementSpec;
    }

    public List<String> getGeneratedFilePaths() {
        return List.copyOf(generatedFilePaths);
    }

    public int getTotalHealingRounds() {
        return totalHealingRounds;
    }

    public record JapAgentResult(
        String taskId,
        String outcome,
        long durationMs,
        int healingRounds,
        int filesGenerated,
        int testsPassed,
        String errorMessage
    ) {
        public static JapAgentResult success(String taskId, long duration, int files, int tests) {
            return new JapAgentResult(taskId, "SUCCESS", duration, 0, files, tests, null);
        }

        public static JapAgentResult healed(String taskId, long duration, int rounds, int files, int tests) {
            return new JapAgentResult(taskId, "HEALED", duration, rounds, files, tests, null);
        }

        public static JapAgentResult failed(String taskId, String error) {
            return new JapAgentResult(taskId, "FAILED", 0, 0, 0, 0, error);
        }

        public static JapAgentResult cancelled(String taskId) {
            return new JapAgentResult(taskId, "CANCELLED", 0, 0, 0, 0, "User cancelled");
        }

        public static JapAgentResult manualIntervention(String taskId, String errorCategory, int rounds) {
            return new JapAgentResult(taskId, "MANUAL_INTERVENTION", 0, rounds, 0, 0, 
                "Healing exhausted for " + errorCategory);
        }
    }

    public record BuildTestResult(
        boolean success,
        boolean healed,
        int healingRounds,
        String errorMessage
    ) {
        public static BuildTestResult ok() {
            return new BuildTestResult(true, false, 0, null);
        }

        public static BuildTestResult healed(int rounds) {
            return new BuildTestResult(true, true, rounds, null);
        }

        public static BuildTestResult exhausted(int rounds, String error) {
            return new BuildTestResult(false, false, rounds, error);
        }

        public static BuildTestResult cancelled() {
            return new BuildTestResult(false, false, 0, "Cancelled");
        }
    }
}
