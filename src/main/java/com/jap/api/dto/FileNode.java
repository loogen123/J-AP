package com.jap.api.dto;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

public record FileNode(
    String name,
    FileType type,
    String relativePath,
    long size,
    Instant lastModified,
    Optional<List<FileNode>> children
) {
    public enum FileType {
        FILE, DIRECTORY
    }

    public static FileNode file(String name, String path, long size) {
        return new FileNode(name, FileType.FILE, path, size, Instant.now(), Optional.empty());
    }

    public static FileNode directory(String name, String path, List<FileNode> children) {
        return new FileNode(name, FileType.DIRECTORY, path, 0, Instant.now(), Optional.of(children));
    }
}
