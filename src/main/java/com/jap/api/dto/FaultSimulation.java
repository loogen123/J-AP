package com.jap.api.dto;

import jakarta.validation.constraints.Pattern;

public record FaultSimulation(
    boolean enabled,
    @Pattern(regexp = "^E[0-9]{2}$", message = "错误类型格式: E01-E07")
    String faultType
) {
    public static final FaultSimulation DISABLED = new FaultSimulation(false, null);
    
    public static FaultSimulation e02() {
        return new FaultSimulation(true, "E02");
    }
    
    public static FaultSimulation e05() {
        return new FaultSimulation(true, "E05");
    }
}
