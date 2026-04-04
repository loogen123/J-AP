package com.jap.api.controller;

import com.jap.config.JapConfigManager;
import com.jap.config.JapConfigManager.JapSettings;
import com.jap.config.JapConfigManager.LlmConfig;
import com.jap.config.JapConfigManager.WorkspaceConfig;
import com.jap.sandbox.exception.SandboxViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final JapConfigManager configManager;

    public ConfigController(JapConfigManager configManager) {
        this.configManager = configManager;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getSettings() {
        JapSettings settings = configManager.getCurrentSettings();
        LlmConfig maskedLlm = new LlmConfig(
            settings.llm().baseUrl(),
            configManager.maskApiKey(settings.llm().apiKey()),
            settings.llm().modelName(),
            settings.llm().temperature(),
            settings.llm().maxTokens(),
            settings.llm().timeoutSeconds()
        );
        
        boolean apiKeyConfigured = settings.llm().apiKey() != null && !settings.llm().apiKey().isBlank();
        
        return ResponseEntity.ok(Map.of(
            "llm", maskedLlm,
            "workspace", settings.workspace(),
            "apiKeyConfigured", apiKeyConfigured
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> updateSettings(@RequestBody JapSettings settings) {
        try {
            String absolutePath = null;
            if (settings.workspace() != null && settings.workspace().path() != null) {
                configManager.setWorkspacePath(settings.workspace().path());
                absolutePath = configManager.getWorkspacePath().toString();
            }
            
            configManager.saveSettings(settings);
            
            if (settings.llm() != null && settings.llm().apiKey() != null && !settings.llm().apiKey().isBlank()) {
                configManager.rebuildChatModel(settings.llm());
                log.info("ChatLanguageModel rebuilt with new LLM config");
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "配置已保存并立即生效",
                "workspace", configManager.getWorkspacePath().toString(),
                "absolutePath", absolutePath,
                "restartRequired", false
            ));
        } catch (IOException e) {
            log.error("Failed to save settings", e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/llm/test")
    public ResponseEntity<Map<String, Object>> testLlmConnection(@RequestBody LlmConfig config) {
        log.info("Testing LLM connection to: {} with model: {}", config.baseUrl(), config.modelName());
        
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "API Key 不能为空"
            ));
        }

        boolean success = configManager.testLlmConnection(config);
        
        if (success) {
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "LLM 连接测试成功"
            ));
        } else {
            return ResponseEntity.ok(Map.of(
                "success", false,
                "error", "LLM 连接测试失败，请检查 API Key 和 Base URL"
            ));
        }
    }

    @PostMapping("/workspace/validate")
    public ResponseEntity<Map<String, Object>> validateWorkspace(@RequestBody Map<String, String> request) {
        String path = request.get("path");
        
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "error", "路径不能为空"
            ));
        }

        try {
            configManager.validatePath(path);
            
            java.nio.file.Path inputPath = java.nio.file.Paths.get(path);
            java.nio.file.Path absolutePath;
            if (inputPath.isAbsolute()) {
                absolutePath = inputPath.normalize();
            } else {
                absolutePath = java.nio.file.Paths.get(System.getProperty("user.dir")).resolve(path).toAbsolutePath().normalize();
            }
            
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "路径验证通过",
                "absolutePath", absolutePath.toString()
            ));
        } catch (IOException e) {
            return ResponseEntity.ok(Map.of(
                "valid", false,
                "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/workspace")
    public ResponseEntity<Map<String, Object>> getWorkspaceInfo() {
        return ResponseEntity.ok(Map.of(
            "path", configManager.getWorkspacePath().toString(),
            "exists", configManager.getWorkspacePath().toFile().exists()
        ));
    }

    @GetMapping("/workspace/choose")
    public ResponseEntity<Map<String, Object>> chooseWorkspace() {
        try {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("选择工作区目录");
            
            javax.swing.JFrame frame = new javax.swing.JFrame();
            frame.setAlwaysOnTop(true);
            frame.setLocationRelativeTo(null);
            
            int result = chooser.showOpenDialog(frame);
            frame.dispose();
            
            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                return ResponseEntity.ok(Map.of("path", chooser.getSelectedFile().getAbsolutePath()));
            }
            return ResponseEntity.ok(Map.of("path", ""));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }
}
