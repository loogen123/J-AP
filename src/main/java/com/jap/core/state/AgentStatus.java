package com.jap.core.state;

public enum AgentStatus {
    IDLE,
    INTENT_ANALYSIS,
    DESIGN,
    WAITING_FOR_APPROVAL,
    CODE_GENERATION,
    BUILD_TEST,
    BUG_FIX,
    COMPLETE,
    MANUAL_INTERVENTION,
    FAILED,
    CANCELLED
}
