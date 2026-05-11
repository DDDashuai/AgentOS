package com.agentos.core.security;

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

    public HumanApprovalInterceptor(List<ToolDefinition> tools) {
        this.toolRegistry = tools.stream()
                .collect(Collectors.toUnmodifiableMap(ToolDefinition::getName, t -> t));
    }

    @Override
    public boolean preHandle(ToolExecutionRequest request) {
        ToolDefinition def = toolRegistry.get(request.toolName());
        if (def != null && def.isDestructive()) {
            log.error("‼️ HUMAN APPROVAL REQUIRED for destructive tool '{}' with args: {} — " +
                            "execution halted. This tool requires explicit human authorization.",
                    request.toolName(), request.args());
            throw new PendingApprovalException(
                    "Tool '" + request.toolName() + "' is destructive and requires human approval");
        }
        return true;
    }
}
