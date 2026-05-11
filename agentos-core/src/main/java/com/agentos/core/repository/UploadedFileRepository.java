package com.agentos.core.repository;

import com.agentos.core.entity.UploadedFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFileEntity, UUID> {
    List<UploadedFileEntity> findBySessionId(UUID sessionId);
    void deleteBySessionId(UUID sessionId);
}
