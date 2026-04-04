package com.jap.sandbox.model;

public enum SandboxOperation {
    READ("READ", "读取文件"),
    WRITE("WRITE", "写入文件"),
    DELETE("DELETE", "删除文件"),
    LIST("LIST", "列出目录"),
    EXECUTE("EXECUTE", "执行进程"),
    UNKNOWN("UNKNOWN", "未知操作");

    private final String code;
    private final String description;

    SandboxOperation(String code, String description) {
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
