package com.agentos.core.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uploaded_files")
public class UploadedFileEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "file_path")
    private String filePath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String headers;

    @Column(name = "row_count")
    private int rowCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UploadedFileEntity() {}

    public UploadedFileEntity(UUID id, UUID sessionId, String originalName, String fileType,
                              String filePath, String headers, int rowCount) {
        this.id = id;
        this.sessionId = sessionId;
        this.originalName = originalName;
        this.fileType = fileType;
        this.filePath = filePath;
        this.headers = headers;
        this.rowCount = rowCount;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getHeaders() { return headers; }
    public void setHeaders(String headers) { this.headers = headers; }
    public int getRowCount() { return rowCount; }
    public void setRowCount(int rowCount) { this.rowCount = rowCount; }
    public Instant getCreatedAt() { return createdAt; }
}
