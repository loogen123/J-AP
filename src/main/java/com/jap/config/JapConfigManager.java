package com.jap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class JapConfigManager {

    private static final Logger log = LoggerFactory.getLogger(JapConfigManager.class);
    private static final String CONFIG_DIR = "config";
    private static final String SETTINGS_FILE = "settings.json";
    private static final int MIN_TIMEOUT_SECONDS = 300;

    private static final Set<String> FORBIDDEN_PATHS = Set.of(
        "/etc", "/root", "/var", "/usr", "/bin", "/sbin", "/boot", "/dev", "/proc", "/sys",
        "C:/Windows", "C:/Program Files", "C:/Program Files (x86)", "C:/Users",
        "/System", "/Library", "/Applications"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReentrantLock lock = new ReentrantLock();

    private volatile ChatLanguageModel chatLanguageModel;
    private volatile JapSettings currentSettings;
    private volatile Path currentWorkspacePath;

    public record JapSettings(
        LlmConfig llm,
        WorkspaceConfig workspace
    ) {
        public static JapSettings defaults(JapProperties properties) {
        String baseUrl = "https://api.deepseek.com";
        String apiKey = "";
        String modelName = "deepseek-chat";
        double temperature = 0.3;
        int maxTokens = 4096;
        int timeoutSeconds = MIN_TIMEOUT_SECONDS;

        if (properties != null && properties.llm() != null) {
            if (properties.llm().baseUrl() != null) {
                baseUrl = properties.llm().baseUrl();
            }
            if (properties.llm().apiKey() != null) {
                apiKey = properties.llm().apiKey();
            }
            if (properties.llm().modelName() != null) {
                modelName = properties.llm().modelName();
            }
            temperature = properties.llm().temperature();
            maxTokens = properties.llm().maxTokens();
            timeoutSeconds = properties.llm().timeoutSeconds();
        }

        return new JapSettings(
            new LlmConfig(baseUrl, apiKey, modelName, temperature, maxTokens, timeoutSeconds),
            new WorkspaceConfig("generated-workspace", false)
        );
    }
    }

    public record LlmConfig(
        String baseUrl,
        String apiKey,
        String modelName,
        double temperature,
        int maxTokens,
        int timeoutSeconds
    ) {}

    public record WorkspaceConfig(
        String path,
        boolean createIfNotExists
    ) {}

    public JapConfigManager(JapProperties properties) {
        this.currentSettings = loadSettings(properties);
        this.currentWorkspacePath = Paths.get(currentSettings.workspace().path()).toAbsolutePath().normalize();
        this.chatLanguageModel = createChatModel(currentSettings.llm());
        log.info("JapConfigManager initialized with workspace: {}", currentWorkspacePath);
    }

    public JapSettings loadSettings(JapProperties properties) {
        Path configPath = Paths.get(CONFIG_DIR, SETTINGS_FILE);
        
        if (Files.exists(configPath)) {
            try {
                String content = Files.readString(configPath);
                JapSettings settings = objectMapper.readValue(content, JapSettings.class);
                log.info("Loaded settings from {}", configPath);
                return settings;
            } catch (IOException e) {
                log.warn("Failed to load settings from {}, using defaults", configPath, e);
            }
        }

        return JapSettings.defaults(properties);
    }

    public void saveSettings(JapSettings settings) throws IOException {
        lock.lock();
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            Path configPath = configDir.resolve(SETTINGS_FILE);
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings);
            Files.writeString(configPath, content);
            
            this.currentSettings = settings;
            log.info("Settings saved to {}", configPath);
        } finally {
            lock.unlock();
        }
    }

    public ChatLanguageModel getChatLanguageModel() {
        return chatLanguageModel;
    }

    public StreamingChatLanguageModel getStreamingChatLanguageModel() {
        return createStreamingChatModel(currentSettings.llm());
    }

    public ChatLanguageModel rebuildChatModel(LlmConfig config) {
        lock.lock();
        try {
            this.chatLanguageModel = createChatModel(config);
            log.info("ChatLanguageModel rebuilt with model: {}", config.modelName());
            return this.chatLanguageModel;
        } finally {
            lock.unlock();
        }
    }

    private ChatLanguageModel createChatModel(LlmConfig config) {
        String apiKey = config.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No API key configured, LLM calls will fail");
            apiKey = "dummy-key";
        }
        int timeoutSeconds = Math.max(config.timeoutSeconds(), MIN_TIMEOUT_SECONDS);

        return OpenAiChatModel.builder()
            .baseUrl(config.baseUrl())
            .apiKey(apiKey)
            .modelName(config.modelName())
            .temperature(config.temperature)
            .maxTokens(config.maxTokens)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    private StreamingChatLanguageModel createStreamingChatModel(LlmConfig config) {
        String apiKey = config.apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("No API key configured, LLM calls will fail");
            apiKey = "dummy-key";
        }
        int timeoutSeconds = Math.max(config.timeoutSeconds(), MIN_TIMEOUT_SECONDS);

        return OpenAiStreamingChatModel.builder()
            .baseUrl(config.baseUrl())
            .apiKey(apiKey)
            .modelName(config.modelName())
            .temperature(config.temperature)
            .maxTokens(config.maxTokens)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    public Path getWorkspacePath() {
        return currentWorkspacePath;
    }

    public void setWorkspacePath(String path) throws IOException {
        validatePath(path);
        
        lock.lock();
        try {
            Path resolvedPath;
            
            Path inputPath = Paths.get(path);
            if (inputPath.isAbsolute()) {
                resolvedPath = inputPath.normalize();
            } else {
                resolvedPath = Paths.get(System.getProperty("user.dir")).resolve(path).toAbsolutePath().normalize();
            }
            
            this.currentWorkspacePath = resolvedPath;
            
            if (!Files.exists(currentWorkspacePath)) {
                Files.createDirectories(currentWorkspacePath);
                log.info("Created workspace directory: {}", currentWorkspacePath);
            }
            
            if (this.currentSettings != null) {
                WorkspaceConfig newWorkspace = new WorkspaceConfig(
                    currentWorkspacePath.toString(),
                    true
                );
                this.currentSettings = new JapSettings(
                    this.currentSettings.llm(),
                    newWorkspace
                );
            }
            
            log.info("Workspace path updated to: {}", currentWorkspacePath);
        } finally {
            lock.unlock();
        }
    }
    
    public String getAbsolutePath(String relativePath) {
        Path workspace = getWorkspacePath();
        if (relativePath == null || relativePath.isBlank()) {
            return workspace.toString();
        }
        return workspace.resolve(relativePath).normalize().toString();
    }

    public void validatePath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException("路径不能为空");
        }

        Path normalizedPath = Paths.get(path).toAbsolutePath().normalize();
        String pathStr = normalizedPath.toString().replace("\\", "/");

        for (String forbidden : FORBIDDEN_PATHS) {
            String normalizedForbidden = forbidden.replace("\\", "/");
            if (pathStr.toLowerCase().startsWith(normalizedForbidden.toLowerCase())) {
                throw new IOException("安全限制：不能使用系统核心目录: " + forbidden);
            }
        }

        if (pathStr.contains("/etc/") || pathStr.contains("/root/") || 
            pathStr.toLowerCase().contains("windows") || pathStr.toLowerCase().contains("system32")) {
            throw new IOException("安全限制：路径包含受保护的系统目录");
        }
    }

    public JapSettings getCurrentSettings() {
        return currentSettings;
    }

    public LlmConfig getLlmConfig() {
        return currentSettings.llm();
    }

    public WorkspaceConfig getWorkspaceConfig() {
        return currentSettings.workspace();
    }

    public boolean testLlmConnection(LlmConfig config) {
        try {
            ChatLanguageModel testModel = createChatModel(config);
            String response = testModel.generate("Say 'OK' if you can hear me.");
            return response != null && !response.isBlank();
        } catch (Exception e) {
            log.error("LLM connection test failed", e);
            return false;
        }
    }

    public String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
