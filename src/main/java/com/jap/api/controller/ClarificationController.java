package com.jap.api.controller;

import com.jap.api.dto.ClarificationMessage;
import com.jap.api.dto.ClarificationStreamRequest;
import com.jap.api.dto.TaskLlmConfig;
import com.jap.config.JapConfigManager;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/clarification")
public class ClarificationController {

    private static final Logger log = LoggerFactory.getLogger(ClarificationController.class);

    private final JapConfigManager configManager;

    public ClarificationController(JapConfigManager configManager) {
        this.configManager = configManager;
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamClarification(@RequestBody ClarificationStreamRequest request) {
        SseEmitter emitter = new SseEmitter(0L);

        CompletableFuture.runAsync(() -> {
            try {
                runClarificationStreaming(request, emitter);
            } catch (Exception e) {
                log.error("Clarification streaming failed", e);
                safeSend(emitter, "error", Map.of("message", e.getMessage()));
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void runClarificationStreaming(ClarificationStreamRequest request, SseEmitter emitter) throws InterruptedException {
        String prompt = buildPrompt(request);
        StringBuilder fullResponse = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        var streamingModel = resolveStreamingModel(request);

        streamingModel.chat(prompt, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse == null || partialResponse.isEmpty()) {
                    return;
                }
                fullResponse.append(partialResponse);
                safeSend(emitter, "token", partialResponse);
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse response) {
                String raw = fullResponse.toString().trim();
                boolean ready = raw.contains("[[READY]]");
                String cleaned = raw
                    .replace("[[READY]]", "")
                    .replace("[[ASK]]", "")
                    .trim();

                safeSend(emitter, "done", Map.of(
                    "text", cleaned,
                    "ready", ready
                ));
                emitter.complete();
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                log.error("Clarification streaming model error", error);
                safeSend(emitter, "error", Map.of("message", error.getMessage()));
                emitter.completeWithError(error);
                latch.countDown();
            }
        });

        latch.await(180, TimeUnit.SECONDS);
    }

    private dev.langchain4j.model.chat.StreamingChatLanguageModel resolveStreamingModel(ClarificationStreamRequest request) {
        TaskLlmConfig llm = request != null ? request.llm() : null;
        if (llm == null || llm.apiKey() == null || llm.apiKey().isBlank()) {
            throw new IllegalArgumentException("Missing task-scoped LLM config for clarification request");
        }

        var base = configManager.getLlmConfig();
        String baseUrl = valueOr(llm.baseUrl(), base.baseUrl());
        String modelName = valueOr(llm.modelName(), base.modelName());
        double temperature = llm.temperature() != null ? llm.temperature() : base.temperature();
        int maxTokens = llm.maxTokens() != null ? llm.maxTokens() : base.maxTokens();
        int timeoutSeconds = llm.timeoutSeconds() != null ? llm.timeoutSeconds() : base.timeoutSeconds();
        timeoutSeconds = Math.max(timeoutSeconds, 30);

        return OpenAiStreamingChatModel.builder()
            .baseUrl(baseUrl)
            .apiKey(llm.apiKey())
            .modelName(modelName)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .logRequests(true)
            .logResponses(true)
            .build();
    }

    private String valueOr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String buildPrompt(ClarificationStreamRequest request) {
        String requirement = request != null && request.requirement() != null ? request.requirement().trim() : "";
        String userMessage = request != null && request.userMessage() != null ? request.userMessage().trim() : "";
        String focus = request != null && request.focus() != null ? request.focus().trim() : "GENERAL";
        List<ClarificationMessage> history = request != null && request.history() != null
            ? request.history()
            : List.of();

        StringBuilder historyText = new StringBuilder();
        for (ClarificationMessage msg : history) {
            if (msg == null || msg.content() == null || msg.content().isBlank()) {
                continue;
            }
            String role = msg.role() == null ? "user" : msg.role().toLowerCase();
            String normalizedRole = "assistant".equals(role) ? "Assistant" : "User";
            historyText.append(normalizedRole).append(": ").append(msg.content().trim()).append("\n");
        }

        String focusGuide = switch (focus.toUpperCase()) {
            case "PRODUCT" -> "产品与业务流程：目标用户、核心场景、主流程、业务规则。";
            case "DESIGN" -> "交互与界面设计：页面结构、关键交互、视觉偏好、易用性约束。";
            case "API" -> "接口与契约：输入输出、鉴权方式、错误码、分页/幂等等接口规则。";
            case "DATA" -> "数据模型与存储：实体关系、字段约束、一致性、读写模式。";
            case "NON_FUNCTIONAL" -> "非功能要求：性能、可用性、安全、可观测性、扩展性。";
            default -> "综合澄清：按最影响落地实现的问题优先。";
        };

        String currentTurn = userMessage.isBlank()
            ? "User: Please ask the most important clarification questions first. If requirement is already clear enough, say we can proceed."
            : "User: " + userMessage;

        return """
            You are J-AP clarification assistant.
            Goal: ask concise clarifying questions before formal requirement analysis.

            Rules:
            1. Reply in Chinese, concise and direct.
            2. Ask at most 1-3 key questions per turn.
            3. If user says \"you can design freely\", \"you decide\", or similar intent, switch to ready state.
            4. If requirement is already clear enough, switch to ready state.
            5. Always append one marker at the end:
               - [[ASK]] when more clarification is needed
               - [[READY]] when analysis can start
            6. Do not output any other control markers.
            7. If Preferred focus is not GENERAL, prioritize questions in that focus.
            8. Focus discipline is mandatory: if Preferred focus is not GENERAL, only ask questions in that focus this turn.

            Original requirement:
            """ + requirement + """

            Preferred focus:
            """ + focus + """

            Focus guide:
            """ + focusGuide + """

            Conversation history:
            """ + historyText + """

            Current user input:
            """ + currentTurn + """
            """;
    }

    private void safeSend(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception ignored) {
        }
    }
}
