package com.agentos.core.state;

import com.agentos.core.tool.ToolExecutionResult;

import java.util.List;

public sealed interface AgentEvent permits
        AgentEvent.ThinkingEvent,
        AgentEvent.ToolExecutionEvent,
        AgentEvent.FinalResponseEvent {

    record ThinkingEvent(String thought) implements AgentEvent {}

    record ToolExecutionEvent(List<ToolExecutionResult> results) implements AgentEvent {}

    record FinalResponseEvent(String response) implements AgentEvent {}
}
