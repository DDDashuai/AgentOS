package com.agentos.core.tool;

import org.springframework.stereotype.Component;

@Component
public class LocalSearchTool implements ToolDefinition {

    @Override
    public String getName() {
        return "local_search";
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }

    @Override
    public boolean isDestructive() {
        return false;
    }
}
