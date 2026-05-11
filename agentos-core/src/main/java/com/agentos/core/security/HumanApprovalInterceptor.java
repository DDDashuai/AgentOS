package com.agentos.core.security;

import com.agentos.core.session.ApprovalService;
import com.agentos.core.tool.ToolDefinition;
import com.agentos.core.tool.ToolExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class HumanApprovalInterceptor implements ToolInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HumanApprovalInterceptor.class);

    private final Map<String, ToolDefinition> toolRegistry;
    private final ApprovalService approvalService;

    public HumanApprovalInterceptor(List<ToolDefinition> tools, ApprovalService approvalService) {
        this.toolRegistry = tools.stream()
                .collect(Collectors.toUnmodifiableMap(ToolDefinition::getName, t -> t));
        this.approvalService = approvalService;
    }

    @Override
    public boolean preHandle(ToolExecutionRequest request) {
        ToolDefinition def = toolRegistry.get(request.toolName());
        if (def != null && def.isDestructive()) {
            // Check if pre-approved for this session
            if (approvalService.isApproved(request.sessionId(), request.toolName())) {
                log.info("Tool '{}' pre-approved for session {}", request.toolName(), request.sessionId());
                return true;
            }
            log.error("‼️ HUMAN APPROVAL REQUIRED for destructive tool '{}' with args: {} — " +
                            "execution halted. This tool requires explicit human authorization.",
                    request.toolName(), request.args());
            throw new PendingApprovalException(
                    "Tool '" + request.toolName() + "' is destructive and requires human approval");
        }
        return true;
    }
}
