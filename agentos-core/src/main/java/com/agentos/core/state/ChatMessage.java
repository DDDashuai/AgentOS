package com.agentos.core.state;

import java.util.Objects;

public record ChatMessage(String role, String content, String toolCallId) {

    public ChatMessage {
        Objects.requireNonNull(role);
        Objects.requireNonNull(content);
    }

    public ChatMessage(String role, String content) {
        this(role, content, null);
    }

    public static ChatMessage user(String text) {
        return new ChatMessage("user", text);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage("assistant", text);
    }

    public static ChatMessage tool(String toolName, String result) {
        return tool(toolName, result, null);
    }

    public static ChatMessage tool(String toolName, String result, String toolCallId) {
        return new ChatMessage("tool", toolName + ":" + result, toolCallId);
    }
}
