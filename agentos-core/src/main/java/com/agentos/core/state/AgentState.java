package com.agentos.core.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record AgentState(
        String sessionId,
        List<ChatMessage> history,
        Map<String, Object> context
) {

    public AgentState {
        history = Collections.unmodifiableList(history);
        context = Collections.unmodifiableMap(context);
    }

    /**
     * Returns a new {@link AgentState} with the given message appended to the
     * history. The original instance is not mutated (immutable state flow).
     */
    public AgentState appendMessage(ChatMessage msg) {
        List<ChatMessage> newHistory = new ArrayList<>(history);
        newHistory.add(msg);
        return new AgentState(sessionId, List.copyOf(newHistory), context);
    }
}
