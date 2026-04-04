package com.jap.api.exception;

public class HealingExhaustedException extends ApiException {
    private final int rounds;
    private final String errorCategory;

    public HealingExhaustedException(String taskId, int rounds, String errorCategory) {
        super("HEALING_EXHAUSTED", 
              "Self-healing exhausted after " + rounds + " rounds for task: " + taskId);
        this.rounds = rounds;
        this.errorCategory = errorCategory;
    }

    public int getRounds() {
        return rounds;
    }

    public String getErrorCategory() {
        return errorCategory;
    }
}
