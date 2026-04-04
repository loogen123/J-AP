package com.jap.sandbox.model;

public enum SecurityLevel {
    LOW("LOW", "低风险，可记录日志"),
    MEDIUM("MEDIUM", "中等风险，需要监控"),
    HIGH("HIGH", "高风险，需要阻止"),
    CRITICAL("CRITICAL", "严重风险，立即终止");

    private final String code;
    private final String description;

    SecurityLevel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
