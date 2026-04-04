package com.jap.llm.tool;

import com.jap.sandbox.exception.SandboxViolationException;
import com.jap.sandbox.filesystem.PathValidator;
import com.jap.sandbox.model.SandboxOperation;
import com.jap.sandbox.model.SandboxViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DefaultCodeFileSystemTools implements CodeFileSystemTools {

    private static final Logger log = LoggerFactory.getLogger(DefaultCodeFileSystemTools.class);

    private static final List<String> ALLOWED_EXTENSIONS = List.of(
        ".java", ".xml", ".yml", ".yaml", ".properties", ".md", ".txt", ".json", ".gradle"
    );

    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1MB
    private static final long MAX_WRITE_SIZE = 512 * 1024; // 512KB

    private final PathValidator pathValidator;

    public DefaultCodeFileSystemTools(PathValidator pathValidator) {
        this.pathValidator = pathValidator;
        log.info("CodeFileSystemTools initialized with sandbox root: {}", pathValidator.getSandboxRoot());
    }

    @Override
    public String readFile(String relativePath) throws SandboxViolationException {
        log.debug("readFile called: {}", relativePath);
        
        Path resolvedPath = pathValidator.validateAndResolve(relativePath);
        
        if (!Files.exists(resolvedPath)) {
            return "File not found: " + relativePath;
        }

        if (!Files.isRegularFile(resolvedPath)) {
            return "Not a regular file: " + relativePath;
        }

        try {
            long size = Files.size(resolvedPath);
            if (size > MAX_FILE_SIZE) {
                return "File too large (" + size + " bytes). Maximum allowed: " + MAX_FILE_SIZE + " bytes";
            }

            String content = Files.readString(resolvedPath, StandardCharsets.UTF_8);
            log.info("File read successfully: {} ({} bytes)", relativePath, size);
            return content;

        } catch (IOException e) {
            log.error("Failed to read file: {} - {}", relativePath, e.getMessage());
            return "Error reading file: " + e.getMessage();
        }
    }

    @Override
    public String writeFile(String relativePath, String content, boolean overwrite) throws SandboxViolationException {
        log.debug("writeFile called: {} (overwrite={})", relativePath, overwrite);
        
        Path resolvedPath = pathValidator.validateAndResolve(relativePath);

        validateFileExtension(relativePath);

        if (content != null && content.length() > MAX_WRITE_SIZE) {
            throw new SandboxViolationException(
                SandboxOperation.WRITE,
                relativePath,
                "Content too large (" + content.length() + " chars). Maximum: " + MAX_WRITE_SIZE,
                com.jap.sandbox.model.SecurityLevel.HIGH
            );
        }

        try {
            Files.createDirectories(resolvedPath.getParent());

            if (Files.exists(resolvedPath) && !overwrite) {
                return "File already exists: " + relativePath + ". Use overwrite=true to replace.";
            }

            Path tempPath = resolvedPath.resolveSibling(resolvedPath.getFileName() + ".tmp");
            Files.writeString(tempPath, content != null ? content : "", StandardCharsets.UTF_8);
            Files.move(tempPath, resolvedPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                       java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            log.info("File written successfully: {} ({} bytes)", relativePath, 
                     content != null ? content.length() : 0);
            return "File written successfully: " + relativePath;

        } catch (IOException e) {
            log.error("Failed to write file: {} - {}", relativePath, e.getMessage());
            return "Error writing file: " + e.getMessage();
        }
    }

    @Override
    public String listFiles(String relativePath) throws SandboxViolationException {
        log.debug("listFiles called: {}", relativePath);
        
        Path resolvedPath = relativePath == null || relativePath.isBlank()
            ? pathValidator.getSandboxRoot()
            : pathValidator.validateAndResolve(relativePath);

        if (!Files.exists(resolvedPath)) {
            return "Directory not found: " + relativePath;
        }

        if (!Files.isDirectory(resolvedPath)) {
            return "Not a directory: " + relativePath;
        }

        try (Stream<Path> stream = Files.list(resolvedPath)) {
            List<String> entries = stream
                .map(p -> formatEntry(p, pathValidator.getSandboxRoot()))
                .sorted()
                .collect(Collectors.toList());

            StringBuilder sb = new StringBuilder();
            sb.append("Directory listing for: ").append(relativePath != null ? relativePath : "/").append("\n");
            sb.append("Total entries: ").append(entries.size()).append("\n\n");

            for (String entry : entries) {
                sb.append(entry).append("\n");
            }

            log.info("Listed {} entries in: {}", entries.size(), relativePath);
            return sb.toString();

        } catch (IOException e) {
            log.error("Failed to list directory: {} - {}", relativePath, e.getMessage());
            return "Error listing directory: " + e.getMessage();
        }
    }

    @Override
    public String deleteFile(String relativePath) throws SandboxViolationException {
        log.debug("deleteFile called: {}", relativePath);
        
        Path resolvedPath = pathValidator.validateAndResolve(relativePath);

        if (!Files.exists(resolvedPath)) {
            return "File not found: " + relativePath;
        }

        if (Files.isDirectory(resolvedPath)) {
            return "Cannot delete directory with deleteFile. Use deleteDirectory instead.";
        }

        try {
            Files.delete(resolvedPath);
            log.info("File deleted: {}", relativePath);
            return "File deleted successfully: " + relativePath;

        } catch (IOException e) {
            log.error("Failed to delete file: {} - {}", relativePath, e.getMessage());
            return "Error deleting file: " + e.getMessage();
        }
    }

    @Override
    public String createDirectory(String relativePath) throws SandboxViolationException {
        log.debug("createDirectory called: {}", relativePath);
        
        Path resolvedPath = pathValidator.validateAndResolve(relativePath);

        if (Files.exists(resolvedPath)) {
            return "Path already exists: " + relativePath;
        }

        try {
            Files.createDirectories(resolvedPath);
            log.info("Directory created: {}", relativePath);
            return "Directory created successfully: " + relativePath;

        } catch (IOException e) {
            log.error("Failed to create directory: {} - {}", relativePath, e.getMessage());
            return "Error creating directory: " + e.getMessage();
        }
    }

    @Override
    public boolean exists(String relativePath) {
        try {
            Path resolvedPath = pathValidator.validateAndResolve(relativePath);
            return Files.exists(resolvedPath);
        } catch (SandboxViolationException e) {
            log.debug("exists check failed for {}: {}", relativePath, e.getMessage());
            return false;
        }
    }

    private void validateFileExtension(String path) throws SandboxViolationException {
        String lowerPath = path.toLowerCase();
        boolean allowed = ALLOWED_EXTENSIONS.stream().anyMatch(lowerPath::endsWith);
        
        if (!allowed) {
            throw new SandboxViolationException(
                SandboxOperation.WRITE,
                path,
                "File extension not allowed. Allowed: " + ALLOWED_EXTENSIONS,
                com.jap.sandbox.model.SecurityLevel.MEDIUM
            );
        }
    }

    private String formatEntry(Path path, Path sandboxRoot) {
        String relativePath = sandboxRoot.relativize(path).toString().replace('\\', '/');
        String name = path.getFileName().toString();
        String type = Files.isDirectory(path) ? "[DIR]" : "[FILE]";
        
        try {
            long size = Files.isDirectory(path) ? 0 : Files.size(path);
            String sizeStr = Files.isDirectory(path) ? "" : String.format(" (%d bytes)", size);
            return String.format("%-6s %s%s", type, name, sizeStr);
        } catch (IOException e) {
            return String.format("%-6s %s", type, name);
        }
    }
}
