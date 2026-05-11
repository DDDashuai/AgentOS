package com.agentos.core.security;

import com.agentos.core.tool.ToolExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ValidationInterceptor implements ToolInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ValidationInterceptor.class);

    @Override
    public boolean preHandle(ToolExecutionRequest request) {
        if (request.toolName() == null || request.toolName().isBlank()) {
            log.warn("ValidationInterceptor: blocked request with null/blank tool name");
            return false;
        }
        if (request.args() == null) {
            log.warn("ValidationInterceptor: blocked request '{}' with null args", request.toolName());
            return false;
        }
        return true;
    }
}
