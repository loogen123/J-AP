package com.jap.api.controller;

import com.jap.config.JapProperties;
import com.jap.sandbox.exception.SandboxViolationException;
import com.jap.sandbox.filesystem.PathValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/preview")
public class FilePreviewController {

    private static final Logger log = LoggerFactory.getLogger(FilePreviewController.class);
    
    private final Path sandboxRoot;
    private final PathValidator pathValidator;

    public FilePreviewController(JapProperties properties, PathValidator pathValidator) {
        this.sandboxRoot = Path.of(properties.sandbox().baseDir()).toAbsolutePath().normalize();
        this.pathValidator = pathValidator;
        log.info("FilePreviewController initialized with sandbox: {}", sandboxRoot);
    }

    @GetMapping("/{*filePath}")
    public ResponseEntity<byte[]> previewFile(@PathVariable String filePath) {
        try {
            String normalizedPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
            Path resolvedPath = pathValidator.validateAndResolve(normalizedPath);
            
            if (!Files.exists(resolvedPath)) {
                log.warn("File not found: {}", resolvedPath);
                return ResponseEntity.notFound().build();
            }
            
            if (Files.isDirectory(resolvedPath)) {
                return ResponseEntity.badRequest()
                    .body("Cannot preview directory".getBytes(StandardCharsets.UTF_8));
            }
            
            byte[] content = Files.readAllBytes(resolvedPath);
            MediaType contentType = determineContentType(normalizedPath);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentType);
            headers.setContentLength(content.length);
            headers.setCacheControl("no-cache");
            
            if (contentType == MediaType.TEXT_HTML) {
                headers.add("X-Frame-Options", "SAMEORIGIN");
            }
            
            log.debug("Previewing file: {} ({})", normalizedPath, contentType);
            return new ResponseEntity<>(content, headers, HttpStatus.OK);
            
        } catch (SandboxViolationException e) {
            log.error("Sandbox violation attempt: {}", filePath, e);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Access denied".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.error("Failed to read file: {}", filePath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to read file".getBytes(StandardCharsets.UTF_8));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listFiles() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> docs = listDirectory("docs");
            Map<String, Object> prototype = listDirectory("prototype");
            Map<String, Object> src = listDirectory("src");
            
            result.put("docs", docs);
            result.put("prototype", prototype);
            result.put("src", src);
            result.put("success", true);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to list files", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    private Map<String, Object> listDirectory(String relativePath) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Path dirPath = pathValidator.validateAndResolve(relativePath);
            
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                result.put("exists", false);
                return result;
            }
            
            result.put("exists", true);
            result.put("path", relativePath);
            
            Files.list(dirPath).forEach(path -> {
                String name = path.getFileName().toString();
                try {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("name", name);
                    fileInfo.put("isDirectory", Files.isDirectory(path));
                    fileInfo.put("size", Files.size(path));
                    fileInfo.put("type", getFileType(name));
                    
                    result.put(name, fileInfo);
                } catch (IOException e) {
                    log.warn("Failed to get file info: {}", name);
                }
            });
            
        } catch (SandboxViolationException e) {
            result.put("exists", false);
            result.put("error", "Access denied");
        }
        
        return result;
    }

    private MediaType determineContentType(String filePath) {
        String lowerPath = filePath.toLowerCase();
        
        if (lowerPath.endsWith(".html") || lowerPath.endsWith(".htm")) {
            return MediaType.TEXT_HTML;
        }
        if (lowerPath.endsWith(".md")) {
            return MediaType.valueOf("text/markdown");
        }
        if (lowerPath.endsWith(".java")) {
            return MediaType.valueOf("text/x-java-source");
        }
        if (lowerPath.endsWith(".xml")) {
            return MediaType.APPLICATION_XML;
        }
        if (lowerPath.endsWith(".json")) {
            return MediaType.APPLICATION_JSON;
        }
        if (lowerPath.endsWith(".yml") || lowerPath.endsWith(".yaml")) {
            return MediaType.valueOf("text/yaml");
        }
        if (lowerPath.endsWith(".css")) {
            return MediaType.valueOf("text/css");
        }
        if (lowerPath.endsWith(".js")) {
            return MediaType.valueOf("application/javascript");
        }
        if (lowerPath.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lowerPath.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        
        return MediaType.TEXT_PLAIN;
    }

    private String getFileType(String fileName) {
        String lowerName = fileName.toLowerCase();
        
        if (lowerName.endsWith(".html") || lowerName.endsWith(".htm")) {
            return "HTML";
        }
        if (lowerName.endsWith(".md")) {
            return "MARKDOWN";
        }
        if (lowerName.endsWith(".java")) {
            return "JAVA";
        }
        if (lowerName.endsWith(".xml")) {
            return "XML";
        }
        if (lowerName.endsWith(".json")) {
            return "JSON";
        }
        if (lowerName.endsWith(".yml") || lowerName.endsWith(".yaml")) {
            return "YAML";
        }
        if (lowerName.endsWith(".css")) {
            return "CSS";
        }
        if (lowerName.endsWith(".js")) {
            return "JAVASCRIPT";
        }
        
        return "UNKNOWN";
    }
}
