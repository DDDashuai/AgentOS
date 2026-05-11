package com.agentos.core.session;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session approval store for Human-in-the-Loop (HITL) pre-approval of
 * destructive tools. After a user approves via the REST endpoint, the tool
 * name is stored for the session so that {@code HumanApprovalInterceptor}
 * allows it through on retry.
 */
@Service
public class ApprovalService {

    private final Map<String, Set<String>> store = new ConcurrentHashMap<>();

    public boolean isApproved(String sessionId, String toolName) {
        if (sessionId == null) return false;
        var approved = store.get(sessionId);
        return approved != null && approved.contains(toolName);
    }

    public void approve(String sessionId, String toolName) {
        store.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(toolName);
    }

    public void clear(String sessionId) {
        store.remove(sessionId);
    }
}
