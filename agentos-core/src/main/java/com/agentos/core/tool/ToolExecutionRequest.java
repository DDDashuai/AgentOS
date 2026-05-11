package com.agentos.core.tool;

import java.util.Map;

public record ToolExecutionRequest(String toolName, Map<String, String> args, String toolCallId) {

    public ToolExecutionRequest(String toolName, Map<String, String> args) {
        this(toolName, args, null);
    }
}
