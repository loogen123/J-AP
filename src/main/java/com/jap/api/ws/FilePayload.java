package com.jap.api.ws;

public record FilePayload(
    String path,
    long size,
    String action,
    String language
) {}
