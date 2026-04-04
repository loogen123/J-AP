package com.jap.llm.dto;

import java.util.List;

public record CodePatchResult(
    boolean success,
    List<CodePatch> patches,
    String summary,
    double confidence
) {
    public static CodePatchResult failure(String reason) {
        return new CodePatchResult(false, List.of(), reason, 0.0);
    }

    public static CodePatchResult single(String filePath, String original, String patched, String description) {
        return new CodePatchResult(
            true,
            List.of(new CodePatch(filePath, original, patched, description, 0, 0)),
            description,
            0.9
        );
    }
}
