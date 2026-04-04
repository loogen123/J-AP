package com.jap.llm.dto;

import java.util.List;

public record DesignDocuments(
    String prd,
    String architecture,
    String prototype,
    List<String> filePaths
) {
    public static DesignDocuments empty() {
        return new DesignDocuments(null, null, null, List.of());
    }

    public boolean hasDocuments() {
        return prd != null || architecture != null || prototype != null;
    }
}
