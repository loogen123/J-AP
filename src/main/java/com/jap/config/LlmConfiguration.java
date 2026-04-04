package com.jap.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LlmConfiguration.class);

    @Bean
    public ChatLanguageModel chatLanguageModel(JapConfigManager configManager) {
        log.info("Using DynamicChatLanguageModel wrapper for hot-reload support");
        return new DynamicChatLanguageModel(configManager);
    }
}
