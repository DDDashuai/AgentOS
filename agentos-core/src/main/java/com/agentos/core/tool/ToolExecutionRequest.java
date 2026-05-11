package com.agentos.core.tool;

import java.util.Map;

public record ToolExecutionRequest(String toolName, Map<String, String> args, String toolCallId, String sessionId) {

    public ToolExecutionRequest(String toolName, Map<String, String> args, String toolCallId) {
        this(toolName, args, toolCallId, null);
    }

    public ToolExecutionRequest(String toolName, Map<String, String> args) {
        this(toolName, args, null, null);
    }
}
