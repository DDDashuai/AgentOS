package com.agentos.core.tool;

public record ToolExecutionResult(String toolName, boolean success, String output, long durationMs, String toolCallId) {

    public ToolExecutionResult(String toolName, boolean success, String output, long durationMs) {
        this(toolName, success, output, durationMs, null);
    }
}
