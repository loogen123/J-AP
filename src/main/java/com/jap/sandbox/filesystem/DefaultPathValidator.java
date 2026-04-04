package com.jap.sandbox.filesystem;

import com.jap.config.JapConfigManager;
import com.jap.sandbox.exception.SandboxViolationException;
import com.jap.sandbox.model.SandboxOperation;
import com.jap.sandbox.model.SecurityLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class DefaultPathValidator implements PathValidator {

    private static final Logger log = LoggerFactory.getLogger(DefaultPathValidator.class);

    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\x00-\\x1f]");
    private static final Pattern TRAVERSAL_PATTERN = Pattern.compile("(^|/)\\.\\.($|/)");
    private static final Pattern ABSOLUTE_PATH_PATTERN = Pattern.compile("^/|^[A-Za-z]:");

    private final JapConfigManager configManager;

    @Autowired
    public DefaultPathValidator(JapConfigManager configManager) {
        this.configManager = configManager;
        this.fixedSandboxRoot = null;
        log.info("PathValidator initialized with dynamic workspace from JapConfigManager");
    }

    private final Path fixedSandboxRoot;

    private DefaultPathValidator(Path sandboxRoot) {
        this.configManager = null;
        this.fixedSandboxRoot = sandboxRoot.toAbsolutePath().normalize();
        log.info("PathValidator initialized with fixed sandbox root for testing: {}", this.fixedSandboxRoot);
    }

    public static DefaultPathValidator forTest(Path sandboxRoot) {
        return new DefaultPathValidator(sandboxRoot);
    }

    @Override
    public Path getSandboxRoot() {
        if (fixedSandboxRoot != null) {
            return fixedSandboxRoot;
        }
        if (configManager != null) {
            return configManager.getWorkspacePath();
        }
        return Paths.get("generated-workspace").toAbsolutePath().normalize();
    }

    @Override
    public Path validateAndResolve(String relativePath) throws SandboxViolationException {
        Objects.requireNonNull(relativePath, "relativePath must not be null");

        if (isTraversalAttempt(relativePath)) {
            log.warn("Path traversal attempt blocked: {}", relativePath);
            throw SandboxViolationException.pathTraversal(relativePath);
        }

        Path normalized;
        try {
            normalized = Paths.get(relativePath).normalize();
        } catch (InvalidPathException e) {
            log.warn("Invalid path rejected: {} - {}", relativePath, e.getMessage());
            throw SandboxViolationException.invalidPath(relativePath, e);
        }

        Path sandboxRoot = getSandboxRoot();
        Path absolute = sandboxRoot.resolve(normalized).normalize();

        Path realPath;
        try {
            realPath = absolute.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            if (!absolute.startsWith(sandboxRoot)) {
                log.warn("Path escapes sandbox root: {} (resolved from: {})", absolute, relativePath);
                throw SandboxViolationException.outsideSandbox(absolute.toString());
            }
            return absolute;
        }

        if (!realPath.startsWith(sandboxRoot)) {
            log.warn("Symlink attack detected: {} resolves to {} outside sandbox {}", 
                absolute, realPath, sandboxRoot);
            throw SandboxViolationException.symlinkAttack(realPath.toString());
        }

        log.debug("Path validated: {} -> {}", relativePath, realPath);
        return realPath;
    }

    @Override
    public void validateAbsolute(Path absolutePath) throws SandboxViolationException {
        Objects.requireNonNull(absolutePath, "absolutePath must not be null");
        
        Path sandboxRoot = getSandboxRoot();
        Path normalized = absolutePath.normalize().toAbsolutePath();
        
        if (!normalized.startsWith(sandboxRoot)) {
            log.warn("Absolute path outside sandbox: {} (sandbox: {})", normalized, sandboxRoot);
            throw new SandboxViolationException(
                SandboxOperation.UNKNOWN,
                normalized.toString(),
                "Absolute path outside sandbox: " + normalized,
                SecurityLevel.CRITICAL
            );
        }
    }

    @Override
    public boolean isTraversalAttempt(String pathInput) {
        if (pathInput == null || pathInput.isBlank()) {
            return true;
        }

        String normalized = pathInput.replace('\\', '/');

        if (TRAVERSAL_PATTERN.matcher(normalized).find()) {
            return true;
        }

        if (ABSOLUTE_PATH_PATTERN.matcher(normalized).find()) {
            return true;
        }

        if (CONTROL_CHAR_PATTERN.matcher(normalized).find()) {
            return true;
        }

        return false;
    }
}
