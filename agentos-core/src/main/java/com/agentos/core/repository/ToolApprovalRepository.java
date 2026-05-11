package com.agentos.core.repository;

import com.agentos.core.entity.ToolApprovalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ToolApprovalRepository extends JpaRepository<ToolApprovalEntity, UUID> {
    Optional<ToolApprovalEntity> findBySessionIdAndToolName(UUID sessionId, String toolName);
    void deleteBySessionId(UUID sessionId);
}
