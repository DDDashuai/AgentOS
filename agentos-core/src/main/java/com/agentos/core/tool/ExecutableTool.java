package com.agentos.core.tool;

/**
 * A {@link ToolDefinition} that can execute its own logic.
 * <p>
 * Tools that perform real work (database queries, file I/O, etc.) implement this
 * interface so the {@link com.agentos.core.engine.ConcurrencyPartitioningEngine}
 * can dispatch execution to them instead of using the simulation fallback.
 */
public interface ExecutableTool extends ToolDefinition {

    ToolExecutionResult execute(ToolExecutionRequest request);
}
