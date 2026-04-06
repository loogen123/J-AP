package com.jap.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitRequest(
    @NotBlank(message = "需求描述不能为空")
    @Size(max = 10000, message = "需求描述不能超过10000字符")
    String requirement,

    TaskOptions options,

    FaultSimulation faultSimulation,

    TaskLlmConfig llm
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
