package com.agentos.core.tool;

import org.springframework.stereotype.Component;

@Component
public class BashExecutionTool implements ToolDefinition {

    @Override
    public String getName() {
        return "bash_execution";
    }

    @Override
    public boolean isConcurrencySafe() {
        return false;
    }

    @Override
    public boolean isDestructive() {
        return true;
    }
}
