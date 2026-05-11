package com.agentos.core.tool;

public interface ToolDefinition {
    String getName();
    boolean isConcurrencySafe();
    boolean isDestructive();
}
