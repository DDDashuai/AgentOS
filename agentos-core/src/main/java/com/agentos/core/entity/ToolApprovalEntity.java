package com.agentos.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tool_approvals")
public class ToolApprovalEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "tool_name", nullable = false)
    private String toolName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ToolApprovalEntity() {}

    public ToolApprovalEntity(UUID id, UUID sessionId, String toolName) {
        this.id = id;
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public Instant getCreatedAt() { return createdAt; }
}
