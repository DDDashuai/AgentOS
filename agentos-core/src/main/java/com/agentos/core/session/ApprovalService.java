package com.agentos.core.session;

import com.agentos.core.entity.ToolApprovalEntity;
import com.agentos.core.repository.ToolApprovalRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Per-session approval store for Human-in-the-Loop (HITL) pre-approval of
 * destructive tools. Backed by the tool_approvals table for auditability.
 */
@Service
public class ApprovalService {

    private final ToolApprovalRepository repository;

    public ApprovalService(ToolApprovalRepository repository) {
        this.repository = repository;
    }

    public boolean isApproved(String sessionId, String toolName) {
        if (sessionId == null) return false;
        try {
            var uuid = UUID.fromString(sessionId);
            return repository.findBySessionIdAndToolName(uuid, toolName).isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void approve(String sessionId, String toolName) {
        try {
            var uuid = UUID.fromString(sessionId);
            // Avoid duplicates
            if (repository.findBySessionIdAndToolName(uuid, toolName).isEmpty()) {
                repository.save(new ToolApprovalEntity(UUID.randomUUID(), uuid, toolName));
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid sessionId: " + sessionId);
        }
    }

    public void clear(String sessionId) {
        try {
            var uuid = UUID.fromString(sessionId);
            repository.deleteBySessionId(uuid);
        } catch (IllegalArgumentException e) {
            // ignore invalid sessionId
        }
    }
}
