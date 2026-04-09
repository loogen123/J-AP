package com.jap.llm.streaming;

import com.jap.api.ws.StompPrototypeProgress;
import com.jap.config.JapConfigManager;
import com.jap.event.TaskEventPublisher;
import com.jap.llm.tool.CodeFileSystemTools;
import com.jap.sandbox.exception.SandboxViolationException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class StreamingHtmlGenerator {

    private static final Logger log = LoggerFactory.getLogger(StreamingHtmlGenerator.class);
    
    private static final int PROGRESS_REPORT_INTERVAL = 500;
    private static final int ESTIMATED_HTML_SIZE = 15000;
    private static final int MAX_CONTINUATION_ROUNDS = 2;
    private static final int CONTINUATION_TAIL_CHARS = 3200;

    private final StreamingChatLanguageModel chatModel;
    private final TaskEventPublisher eventPublisher;
    private final CodeFileSystemTools fileTools;
    private final JapConfigManager configManager;
    private final String taskId;

    public StreamingHtmlGenerator(
            StreamingChatLanguageModel chatModel,
            TaskEventPublisher eventPublisher,
            CodeFileSystemTools fileTools,
            JapConfigManager configManager,
            String taskId) {
        this.chatModel = chatModel;
        this.eventPublisher = eventPublisher;
        this.fileTools = fileTools;
        this.configManager = configManager;
        this.taskId = taskId;
    }

    public StreamingResult generateWithStreaming(
            String prompt,
            String outputPath) {
        
        AtomicInteger charsWritten = new AtomicInteger(0);
        java.util.concurrent.atomic.AtomicLong lastProgressReportTime = new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());
        AtomicReference<Throwable> error = new AtomicReference<>(null);
        AtomicReference<Boolean> completed = new AtomicReference<>(false);

        Path tempFile = null;
        BufferedWriter writer = null;
        BufferedWriter finalWriter;

        try {
            Path workspaceRoot = getWorkspaceRoot();
            Path fullOutputPath = workspaceRoot.resolve(outputPath);
            Files.createDirectories(fullOutputPath.getParent());
            
            tempFile = fullOutputPath.resolveSibling(fullOutputPath.getFileName() + ".partial");
            writer = Files.newBufferedWriter(tempFile);
            finalWriter = writer;

            log.info("[{}] Starting streaming HTML generation to: {}", taskId, outputPath);
            
            publishProgress(0, "开始生成 HTML 原型...", 0, false, false);

            String systemPrompt = buildSystemPrompt();
            String fullPrompt = systemPrompt + "\n\n" + prompt;

            java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();

            // 使用真正的流式API，边接收token边写入文件
            chatModel.chat(fullPrompt, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    try {
                        if (partialResponse != null && !partialResponse.isEmpty()) {
                            // 实时写入文件
                            finalWriter.write(partialResponse);
                            
                            int written = charsWritten.addAndGet(partialResponse.length());
                            long now = System.currentTimeMillis();
                            
                            if (now - lastProgressReportTime.get() > PROGRESS_REPORT_INTERVAL) {
                                finalWriter.flush();
                                lastProgressReportTime.set(now);
                                
                                int percentage = Math.min(95, (written * 100) / ESTIMATED_HTML_SIZE);
                                String lastChunk = partialResponse.length() > 50 ? partialResponse.substring(0, 50) + "..." : partialResponse;
                                publishProgress(written, lastChunk, percentage, false, false);
                            }
                        }
                    } catch (IOException e) {
                        log.error("[{}] Error writing to file: {}", taskId, e.getMessage());
                        error.set(e);
                        future.completeExceptionally(e);
                    }
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                    try {
                        finalWriter.flush();
                    } catch (IOException e) {
                        log.warn("[{}] Failed to flush writer on complete", taskId, e);
                    }
                    completed.set(true);
                    future.complete(null);
                }

                @Override
                public void onError(Throwable throwable) {
                    log.error("[{}] Streaming error: {}", taskId, throwable.getMessage());
                    error.set(throwable);
                    future.completeExceptionally(throwable);
                }
            });
            
            try {
                future.join();
            } catch (Exception e) {
                log.error("[{}] Error waiting for streaming: {}", taskId, e.getMessage());
            }

        } catch (Exception e) {
            error.set(e);
            log.error("[{}] Error during streaming generation: {}", taskId, e.getMessage());
            
            // 捕获超时异常，但不删除文件，标记为部分完成
            if (charsWritten.get() > 0) {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
                publishProgress(charsWritten.get(), "生成中断，已保存部分内容: " + errorMessage, 
                    Math.min(95, (charsWritten.get() * 100) / ESTIMATED_HTML_SIZE), false, true);
            }
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    log.warn("[{}] Failed to close writer: {}", taskId, e.getMessage());
                }
            }
        }

        try {
            if (tempFile != null && Files.exists(tempFile)) {
                Path workspaceRoot = getWorkspaceRoot();
                Path fullOutputPath = workspaceRoot.resolve(outputPath);
                
                boolean hasClosingHtml = containsClosingHtmlTag(tempFile);
                if (!hasClosingHtml && charsWritten.get() > 0) {
                    for (int i = 1; i <= MAX_CONTINUATION_ROUNDS; i++) {
                        publishProgress(charsWritten.get(),
                            "HTML 未闭合，自动续写中(" + i + "/" + MAX_CONTINUATION_ROUNDS + ")",
                            Math.min(98, (charsWritten.get() * 100) / ESTIMATED_HTML_SIZE),
                            false,
                            true);
                        boolean appended = continueHtmlStreaming(tempFile, charsWritten, i);
                        if (!appended) break;
                        hasClosingHtml = containsClosingHtmlTag(tempFile);
                        if (hasClosingHtml) {
                            completed.set(true);
                            error.set(null);
                            break;
                        }
                    }
                }

                if (completed.get() && error.get() == null) {
                    Files.move(tempFile, fullOutputPath, 
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    
                    publishProgress(charsWritten.get(), "HTML 原型生成完成", 100, true, false);
                    log.info("[{}] HTML prototype completed: {} chars", taskId, charsWritten.get());
                    
                    return new StreamingResult(true, charsWritten.get(), null);
                } else if (charsWritten.get() > 0) {
                    Files.move(tempFile, fullOutputPath, 
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    
                    publishProgress(charsWritten.get(), "HTML 原型部分完成", 
                        Math.min(100, (charsWritten.get() * 100) / ESTIMATED_HTML_SIZE), false, true);
                    log.warn("[{}] HTML prototype partially completed: {} chars", taskId, charsWritten.get());
                    return new StreamingResult(false, charsWritten.get(), error.get());
                } else {
                    Files.deleteIfExists(tempFile);
                    return new StreamingResult(false, 0, error.get());
                }
            }
        } catch (Exception e) {
            log.error("[{}] Failed to finalize output file: {}", taskId, e.getMessage());
            error.set(e);
        }

        return new StreamingResult(false, charsWritten.get(), error.get());
    }

    private boolean continueHtmlStreaming(Path partialFile, AtomicInteger charsWritten, int round) {
        try {
            String existing = Files.readString(partialFile);
            if (existing == null || existing.isBlank()) return false;
            if (containsClosingHtmlTag(partialFile)) return true;

            String tail = existing.length() > CONTINUATION_TAIL_CHARS
                ? existing.substring(existing.length() - CONTINUATION_TAIL_CHARS)
                : existing;

            String continuationPrompt = """
                Continue writing ONLY the missing tail of this HTML file.
                Do not repeat existing content.
                Ensure the final output becomes a complete, valid HTML document and closes all tags.

                Existing HTML tail:
                """ + tail;

            java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();
            try (BufferedWriter appendWriter = Files.newBufferedWriter(partialFile, StandardOpenOption.APPEND)) {
                chatModel.chat(continuationPrompt, new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        try {
                            if (partialResponse != null && !partialResponse.isEmpty()) {
                                appendWriter.write(partialResponse);
                                charsWritten.addAndGet(partialResponse.length());
                            }
                        } catch (IOException e) {
                            future.completeExceptionally(e);
                        }
                    }

                    @Override
                    public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                        try {
                            appendWriter.flush();
                        } catch (IOException ignored) {
                        }
                        future.complete(null);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        future.completeExceptionally(throwable);
                    }
                });
                future.join();
            }

            log.info("[{}] Continuation round {} appended, total chars={}", taskId, round, charsWritten.get());
            return true;
        } catch (Exception e) {
            log.warn("[{}] Continuation round {} failed: {}", taskId, round, e.getMessage());
            return false;
        }
    }

    private boolean containsClosingHtmlTag(Path file) {
        try {
            String content = Files.readString(file);
            if (content == null) return false;
            String lower = content.toLowerCase();
            return lower.contains("</html>");
        } catch (Exception e) {
            return false;
        }
    }

    private Path getWorkspaceRoot() {
        if (configManager != null) {
            return configManager.getWorkspacePath();
        }
        return Path.of("generated-workspace").toAbsolutePath().normalize();
    }

    private void publishProgress(int charsWritten, String lastChunk, int percentage, boolean complete, boolean partial) {
        String truncatedChunk = lastChunk != null && lastChunk.length() > 100 
            ? lastChunk.substring(0, 100) 
            : lastChunk;
            
        eventPublisher.publish(taskId, new StompPrototypeProgress(
            charsWritten,
            truncatedChunk,
            percentage,
            complete,
            partial
        ));
    }

    private String buildSystemPrompt() {
        return """
            你是前端原型生成器。生成完整HTML文件。
            
            必须规则：
            1. 只返回HTML代码，不含markdown标记
            2. 使用CDN引入Tailwind CSS（https://cdn.jsdelivr.net/npm/tailwindcss@3）
            3. 代码简洁，避免复杂JavaScript
            4. 界面使用中文
            5. 从<!DOCTYPE html>开始直接输出
            """;
    }

    public record StreamingResult(
        boolean success,
        int charsWritten,
        Throwable error
    ) {
        public boolean isPartial() {
            return !success && charsWritten > 0;
        }
    }
}
