package com.jap.llm.dto;

public record CodePatch(
    String filePath,
    String originalContent,
    String patchedContent,
    String description,
    int lineStart,
    int lineEnd
) {
    public GeneratedFile toGeneratedFile() {
        return new GeneratedFile(filePath, patchedContent, "JAVA", description);
    }
}
