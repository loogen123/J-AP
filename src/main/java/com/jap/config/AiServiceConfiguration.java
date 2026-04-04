package com.jap.config;

import com.jap.llm.service.AnalysisAiService;
import com.jap.llm.service.CodeGeneratorAiService;
import com.jap.llm.service.DesignAiService;
import com.jap.llm.service.FixAiService;
import com.jap.llm.tool.CodeFileSystemTools;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiServiceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiServiceConfiguration.class);

    @Bean
    public AnalysisAiService analysisAiService(ChatLanguageModel chatLanguageModel) {
        log.info("Building AnalysisAiService with ChatLanguageModel");
        
        return AiServices.builder(AnalysisAiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }

    @Bean
    public CodeGeneratorAiService codeGeneratorAiService(ChatLanguageModel chatLanguageModel,
                                                          CodeFileSystemTools fileSystemTools) {
        log.info("Building CodeGeneratorAiService with ChatLanguageModel and CodeFileSystemTools");
        
        return AiServices.builder(CodeGeneratorAiService.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(fileSystemTools)
                .build();
    }

    @Bean
    public FixAiService fixAiService(ChatLanguageModel chatLanguageModel,
                                      CodeFileSystemTools fileSystemTools) {
        log.info("Building FixAiService with ChatLanguageModel and CodeFileSystemTools");
        
        return AiServices.builder(FixAiService.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(fileSystemTools)
                .build();
    }

    @Bean
    public DesignAiService designAiService(ChatLanguageModel chatLanguageModel) {
        log.info("Building DesignAiService with ChatLanguageModel");
        
        return AiServices.builder(DesignAiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
    }
}
