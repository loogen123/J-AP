package com.jap.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record GeneratedFile(
    @JsonProperty(required = true)
    @JsonPropertyDescription("Relative file path from project root (e.g., src/main/java/com/example/User.java)")
    String path,

    @JsonProperty(required = true)
    @JsonPropertyDescription("Complete file content including package declaration, imports, and class definition")
    String content,

    @JsonPropertyDescription("Programming language (JAVA, XML, YAML, PROPERTIES)")
    String language,

    @JsonPropertyDescription("Brief description of what this file contains")
    String description
) {
    public String getLanguage() {
        if (language != null && !language.isBlank()) {
            return language;
        }
        if (path != null) {
            if (path.endsWith(".java")) return "JAVA";
            if (path.endsWith(".xml")) return "XML";
            if (path.endsWith(".yml") || path.endsWith(".yaml")) return "YAML";
            if (path.endsWith(".properties")) return "PROPERTIES";
        }
        return "UNKNOWN";
    }
}
