package com.jap.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

public record CodeGenerationResult(
    @JsonProperty(required = true)
    @JsonPropertyDescription("List of generated files")
    List<GeneratedFile> files,

    @JsonPropertyDescription("Summary of the generation process")
    String summary,

    @JsonPropertyDescription("Any warnings or notes about the generated code")
    List<String> notes
) {
    public static CodeGenerationResult empty() {
        return new CodeGenerationResult(List.of(), "No files generated", List.of());
    }
}
