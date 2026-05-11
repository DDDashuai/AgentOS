package com.agentos.core.security;

import com.agentos.core.tool.ToolExecutionRequest;

public interface ToolInterceptor {

    /**
     * Intercept a tool execution request before it is executed.
     *
     * @param request the tool execution request
     * @return {@code true} if execution should proceed, {@code false} to block
     * @throws PendingApprovalException if human approval is required
     */
    boolean preHandle(ToolExecutionRequest request);
}
