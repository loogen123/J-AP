package com.jap.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record LogPageResponse(
    String taskId,
    long totalCount,
    int offset,
    int limit,
    boolean hasMore,
    List<LogEntry> logs
) {}
