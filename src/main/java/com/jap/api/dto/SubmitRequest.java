package com.jap.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitRequest(
    @NotBlank(message = "需求描述不能为空")
    @Size(max = 10000, message = "需求描述不能超过10000字符")
    String requirement,

    TaskOptions options,

    FaultSimulation faultSimulation,

    TaskLlmConfig llm,

    @Size(max = 50000, message = "analysisPromptOverride cannot exceed 50000 characters")
    String analysisPromptOverride
) {
    public SubmitRequest {
        if (options == null) {
            options = TaskOptions.DEFAULTS;
        }
        if (faultSimulation == null) {
            faultSimulation = new FaultSimulation(false, null);
        }
    }
}
