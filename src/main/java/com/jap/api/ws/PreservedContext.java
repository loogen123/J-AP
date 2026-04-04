package com.jap.api.ws;

public record PreservedContext(
    boolean originalRequirement,
    boolean allGeneratedFiles,
    boolean healingHistory,
    boolean errorReports,
    boolean vectorSearchResults
) {
    public static final PreservedContext ALL = new PreservedContext(true, true, true, true, true);
}
