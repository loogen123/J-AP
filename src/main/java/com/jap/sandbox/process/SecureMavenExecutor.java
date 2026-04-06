package com.jap.sandbox.process;

import com.jap.config.JapProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SecureMavenExecutor {

    private static final Logger log = LoggerFactory.getLogger(SecureMavenExecutor.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    private static final Pattern MAVEN_ERROR_PATTERN = Pattern.compile(
        "\\[ERROR\\]\\s+(.+?)\\[(\\d+),(\\d+)\\]\\s+(.+?)(?::\\s*(.+))?$"
    );
    private static final Pattern MAVEN_ERROR_ALT_PATTERN = Pattern.compile(
        "\\[ERROR\\]\\s+(.+?):\\[(\\d+),(\\d+)\\]\\s+(.+?)(?::\\s*(.+))?$"
    );
    private static final Pattern SIMPLE_ERROR_PATTERN = Pattern.compile(
        "\\[ERROR\\]\\s+(?:Failed to execute|COMPILATION ERROR|error:|cannot find symbol|package .* does not exist)(.+)",
        Pattern.CASE_INSENSITIVE
    );

    public interface OutputListener {
        void onOutput(String line, boolean isError);
    }

    private final Path sandboxRoot;
    private final Duration maxExecutionTime;

    public SecureMavenExecutor(JapProperties properties) {
        this.sandboxRoot = Path.of(properties.sandbox().baseDir()).toAbsolutePath();
        this.maxExecutionTime = properties.sandbox().process().maxExecutionTime();
        log.info("SecureMavenExecutor initialized: sandboxRoot={}, timeout={}", 
                 sandboxRoot, maxExecutionTime);
    }

    public BuildResult executeCompile(Path projectPath) {
        return executeCompile(projectPath, maxExecutionTime);
    }

    public BuildResult executeCompile(Path projectPath, Duration timeout) {
        return executeCompileWithStreaming(projectPath, timeout, null);
    }

    public BuildResult executeCompileWithStreaming(Path projectPath, Duration timeout, OutputListener listener) {
        Path absoluteProjectPath = sandboxRoot.resolve(projectPath).toAbsolutePath();
        
        if (!absoluteProjectPath.startsWith(sandboxRoot)) {
            log.error("Path escape attempt: {} is outside sandbox {}", 
                     absoluteProjectPath, sandboxRoot);
            return BuildResult.failure(-1, "", "Path outside sandbox");
        }

        File projectDir = absoluteProjectPath.toFile();
        if (!projectDir.exists()) {
            log.error("Project directory does not exist: {}", absoluteProjectPath);
            return BuildResult.failure(-1, "", "Project directory not found");
        }

        log.info("Starting Maven compile in: {}", absoluteProjectPath);
        Instant startTime = Instant.now();

        try {
            ProcessBuilder processBuilder = createSecureProcessBuilder(projectDir);
            
            Process process = processBuilder.start();
            
            StringBuilder outputBuilder = new StringBuilder();
            StringBuilder errorBuilder = new StringBuilder();
            
            Thread outputThread = new Thread(() -> 
                readStreamWithCallback(process.getInputStream(), outputBuilder, listener, false));
            Thread errorThread = new Thread(() -> 
                readStreamWithCallback(process.getErrorStream(), errorBuilder, listener, true));
            
            outputThread.start();
            errorThread.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            
            if (!finished) {
                process.destroyForcibly();
                log.warn("Maven build timed out after {}ms", timeout.toMillis());
                return BuildResult.timeout(outputBuilder.toString());
            }

            outputThread.join(1000);
            errorThread.join(1000);

            Duration duration = Duration.between(startTime, Instant.now());
            int exitCode = process.exitValue();
            String output = outputBuilder.toString();
            String errorOutput = errorBuilder.toString();

            log.info("Maven build completed: exitCode={}, duration={}ms", exitCode, duration.toMillis());

            if (exitCode == 0) {
                return BuildResult.success(duration, output);
            } else {
                List<CompileError> errors = parseErrors(output + "\n" + errorOutput);
                log.warn("Maven build failed with {} errors", errors.size());
                return BuildResult.failure(exitCode, output, errorOutput, duration, errors);
            }

        } catch (IOException e) {
            log.error("Failed to execute Maven", e);
            return BuildResult.failure(-1, "", "Failed to execute Maven: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Maven execution interrupted", e);
            return BuildResult.failure(-1, "", "Execution interrupted");
        }
    }

    private void readStreamWithCallback(java.io.InputStream inputStream, StringBuilder builder, 
                                         OutputListener listener, boolean isError) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
                if (listener != null) {
                    listener.onOutput(line, isError);
                }
            }
        } catch (IOException e) {
            log.warn("Error reading stream", e);
        }
    }

    private ProcessBuilder createSecureProcessBuilder(File workingDir) {
        String taskLocalRepo = new File(workingDir, ".m2/repository").getAbsolutePath();
        List<String> command = List.of(
            "mvn", "clean", "compile", 
            "-B", 
            "-Dstyle.color=never",
            "-Dmaven.test.skip=true",
            "-Dmaven.javadoc.skip=true",
            "-Dmaven.source.skip=true",
            "-Dcheckstyle.skip=true",
            "-Dpmd.skip=true",
            "-Dspotbugs.skip=true",
            "-Denforcer.skip=true",
            "-Dmaven.repo.local=" + taskLocalRepo
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir);
        pb.redirectErrorStream(false);

        Map<String, String> env = pb.environment();
        env.clear();
        
        env.put("PATH", System.getenv("PATH"));
        env.put("JAVA_HOME", System.getenv("JAVA_HOME"));
        env.put("M2_HOME", System.getenv("M2_HOME"));
        env.put("MAVEN_OPTS", "-Xmx512m -XX:+HeapDumpOnOutOfMemoryError");
        env.put("http.proxyHost", "");
        env.put("http.proxyPort", "");
        env.put("https.proxyHost", "");
        env.put("https.proxyPort", "");
        env.put("http.nonProxyHosts", "localhost|127.0.0.1");
        
        env.put("MAVEN_OFFLINE", "true");
        
        log.debug("Secure environment configured: offline mode, no proxy");

        return pb;
    }

    private void readStream(java.io.InputStream inputStream, StringBuilder builder) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
        } catch (IOException e) {
            log.warn("Error reading stream", e);
        }
    }

    private List<CompileError> parseErrors(String output) {
        List<CompileError> errors = new ArrayList<>();
        String[] lines = output.split("\n");

        for (String line : lines) {
            Matcher matcher = MAVEN_ERROR_PATTERN.matcher(line);
            if (matcher.find()) {
                errors.add(CompileError.of(
                    matcher.group(1).trim(),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    "COMPILER_ERROR",
                    matcher.group(4).trim() + 
                        (matcher.group(5) != null ? ": " + matcher.group(5).trim() : "")
                ));
                continue;
            }

            matcher = MAVEN_ERROR_ALT_PATTERN.matcher(line);
            if (matcher.find()) {
                errors.add(CompileError.of(
                    matcher.group(1).trim(),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3)),
                    "COMPILER_ERROR",
                    matcher.group(4).trim() + 
                        (matcher.group(5) != null ? ": " + matcher.group(5).trim() : "")
                ));
                continue;
            }

            if (line.contains("[ERROR]") && 
                (line.contains("cannot find symbol") || 
                 line.contains("package") && line.contains("does not exist") ||
                 line.contains("cannot be applied to") ||
                 line.contains("incompatible types") ||
                 line.contains("method") && line.contains("cannot be referenced"))) {
                errors.add(CompileError.of(
                    "unknown",
                    0, 0,
                    "COMPILER_ERROR",
                    line.replace("[ERROR]", "").trim()
                ));
            }
        }

        return errors;
    }

    public Path getSandboxRoot() {
        return sandboxRoot;
    }
}
