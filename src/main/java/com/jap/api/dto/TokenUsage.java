package com.jap.api.dto;

public record TokenUsage(
    int input,
    int output
) {
    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(
            this.input + other.input,
            this.output + other.output
        );
    }
}
