package com.jap.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.List;

public class DynamicChatLanguageModel implements ChatLanguageModel {

    private final JapConfigManager configManager;

    public DynamicChatLanguageModel(JapConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public String generate(String userMessage) {
        return configManager.getChatLanguageModel().generate(userMessage);
    }

    @Override
    public Response<dev.langchain4j.data.message.AiMessage> generate(List<ChatMessage> messages) {
        return configManager.getChatLanguageModel().generate(messages);
    }
}
