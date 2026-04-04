package com.jap.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record TaskOptions(
    @Pattern(regexp = "^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$", message = "包名格式不合法")
    String packageName,

    @Pattern(regexp = "^(MYSQL|H2|POSTGRESQL)$", message = "仅支持 MYSQL/H2/POSTGRESQL")
    String databaseType,

    boolean includeTests,

    @Min(1) @Max(10)
    Integer maxFixRetries,

    List<@Pattern(regexp = "^[A-Z_0-9]+$") String> techStack
) {
    public static final TaskOptions DEFAULTS = new TaskOptions(
        "com.example.generated",
        "MYSQL",
        true,
        3,
        List.of("JDK_25", "SPRING_BOOT_34", "LANGCHAIN4J_10", "MYSQL_9")
    );

    public TaskOptions {
        if (packageName == null || packageName.isBlank()) {
            packageName = "com.example.generated";
        }
        if (databaseType == null || databaseType.isBlank()) {
            databaseType = "MYSQL";
        }
        if (maxFixRetries == null) {
            maxFixRetries = 3;
        }
        if (techStack == null || techStack.isEmpty()) {
            techStack = List.of("JDK_25", "SPRING_BOOT_34", "LANGCHAIN4J_10", "MYSQL_9");
        }
    }
}
