package com.jap.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record FileTreeResponse(
    String taskId,
    String basePath,
    List<FileNode> files,
    int totalFiles,
    long totalSizeBytes
) {}
