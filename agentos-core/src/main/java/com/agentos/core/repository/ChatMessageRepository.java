package com.agentos.core.repository;

import com.agentos.core.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);
    void deleteBySessionId(UUID sessionId);
}
