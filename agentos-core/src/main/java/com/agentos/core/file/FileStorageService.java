package com.agentos.core.file;

import com.agentos.core.entity.UploadedFileEntity;
import com.agentos.core.repository.UploadedFileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session file storage backed by disk + database metadata.
 * Provides an in-memory cache of parsed UploadedFile objects for hot access.
 * On restart, files are loaded lazily from disk when requested.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final UploadedFileRepository repository;
    private final FileParserService fileParser;
    private final ObjectMapper objectMapper;
    private final Path uploadDir;

    /** Hot cache: sessionId -> (fileId -> UploadedFile) */
    private final Map<String, Map<String, UploadedFile>> cache = new ConcurrentHashMap<>();

    public FileStorageService(UploadedFileRepository repository, FileParserService fileParser,
                              ObjectMapper objectMapper,
                              @Value("${agentos.upload.dir:uploads}") String uploadDirPath) {
        this.repository = repository;
        this.fileParser = fileParser;
        this.objectMapper = objectMapper;
        this.uploadDir = Path.of(uploadDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create upload directory: " + this.uploadDir, e);
        }
    }

    /**
     * Store a parsed file with its raw bytes to disk and persist metadata to DB.
     */
    public void store(String sessionId, UploadedFile file, byte[] rawBytes) {
        Path sessionDir = sessionDir(sessionId);
        String fileName = file.fileId() + "_" + file.originalFilename();
        Path target = sessionDir.resolve(fileName);
        try {
            Files.createDirectories(sessionDir);
            Files.write(target, rawBytes);
        } catch (IOException e) {
            log.warn("Failed to write raw file to disk: {}", e.getMessage());
            target = null;
        }

        // Persist metadata to DB
        try {
            String headersJson = objectMapper.writeValueAsString(file.headers());
            var entity = new UploadedFileEntity(
                    UUID.fromString(file.fileId()),
                    uuidOf(sessionId),
                    file.originalFilename(),
                    file.fileType(),
                    target != null ? target.toString() : null,
                    headersJson,
                    file.totalRowCount()
            );
            repository.save(entity);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("Failed to persist file metadata: {}", e.getMessage());
        }

        // Update cache
        cache.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(file.fileId(), file);
        log.info("[{}] Stored file: {} ({} rows)", sessionId, file.originalFilename(), file.totalRowCount());
    }

    public UploadedFile get(String sessionId, String fileIdOrName) {
        // Lazily restore session cache from DB if needed
        Map<String, UploadedFile> sessionCache = cache.get(sessionId);
        if (sessionCache == null) {
            restoreSession(sessionId);
            sessionCache = cache.get(sessionId);
        }

        if (sessionCache == null) return null;

        UploadedFile f = sessionCache.get(fileIdOrName);
        if (f == null) {
            f = sessionCache.values().stream()
                    .filter(u -> u.originalFilename().equals(fileIdOrName))
                    .findFirst().orElse(null);
        }

        // If the cached file has empty rows but metadata says it has data, reload from disk
        if (f != null && f.rows().isEmpty() && (f.totalRowCount() > 0 || f.previewRows().isEmpty())) {
            UploadedFile reloaded = reloadFromDisk(sessionId, f);
            if (reloaded != null) {
                sessionCache.put(f.fileId(), reloaded);
                f = reloaded;
            }
        }

        return f;
    }

    public List<UploadedFile> list(String sessionId) {
        // Check cache first
        Map<String, UploadedFile> sessionCache = cache.get(sessionId);
        if (sessionCache != null) return List.copyOf(sessionCache.values());

        // Fall back to DB (restore session on demand)
        restoreSession(sessionId);
        sessionCache = cache.get(sessionId);
        return sessionCache == null ? List.of() : List.copyOf(sessionCache.values());
    }

    /** Build a text description of all uploaded files for a session. */
    public String buildDescription(String sessionId) {
        List<UploadedFile> files = list(sessionId);
        if (files.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\nUPLOADED FILES:\n");
        for (UploadedFile f : files) {
            sb.append(f.tableDescription()).append("\n");
            if (!f.previewRows().isEmpty()) {
                sb.append("    Preview (first ").append(f.previewRows().size()).append(" rows):\n");
                for (int i = 0; i < f.previewRows().size(); i++) {
                    sb.append("      Row ").append(i + 1).append(": ").append(f.previewRows().get(i)).append("\n");
                }
            }
        }
        return sb.toString();
    }

    public void clearSession(String sessionId) {
        // Remove from disk
        Path sessionDir = sessionDir(sessionId);
        try {
            if (Files.exists(sessionDir)) {
                try (var files = Files.walk(sessionDir)) {
                    files.sorted(Comparator.reverseOrder())
                            .forEach(p -> {
                                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                            });
                }
            }
        } catch (IOException e) {
            log.warn("Failed to clean up session directory: {}", e.getMessage());
        }

        // Remove from DB
        try {
            repository.deleteBySessionId(uuidOf(sessionId));
        } catch (IllegalArgumentException e) {
            // ignore
        }

        // Clear cache
        cache.remove(sessionId);
        log.info("[{}] Cleared uploaded files", sessionId);
    }

    /**
     * Reload a session's file metadata from the database into the cache.
     * The actual file data is not re-parsed — only the metadata is available
     * until QueryUploadedDataTool triggers a re-parse.
     */
    public void restoreSession(String sessionId) {
        try {
            var entities = repository.findBySessionId(uuidOf(sessionId));
            if (entities.isEmpty()) return;
            Map<String, UploadedFile> sessionCache = new ConcurrentHashMap<>();
            for (var entity : entities) {
                List<String> headers;
                try {
                    headers = objectMapper.readValue(entity.getHeaders(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                } catch (Exception e) {
                    headers = List.of();
                }
                // Create a minimal UploadedFile with metadata only
                var uploaded = new UploadedFile(
                        entity.getId().toString(),
                        entity.getOriginalName(),
                        entity.getFileType(),
                        headers,
                        List.of(),  // rows — empty, will be loaded on demand
                        entity.getRowCount(),
                        null,
                        List.of()   // previewRows — empty
                );
                sessionCache.put(entity.getId().toString(), uploaded);
            }
            cache.put(sessionId, sessionCache);
            log.info("[{}] Restored {} files from database", sessionId, entities.size());
        } catch (IllegalArgumentException e) {
            log.warn("Failed to restore session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * Try to reload a file's data from disk. Returns null if the raw file
     * cannot be found or parsed.
     */
    private UploadedFile reloadFromDisk(String sessionId, UploadedFile cached) {
        Path sessionDir = sessionDir(sessionId);
        if (!Files.exists(sessionDir)) return null;

        String prefix = cached.fileId() + "_";
        try (var files = Files.list(sessionDir)) {
            var filePath = files.filter(p -> p.getFileName().toString().startsWith(prefix))
                    .findFirst().orElse(null);
            if (filePath == null) return null;

            String filename = filePath.getFileName().toString().substring(prefix.length());
            byte[] rawBytes = Files.readAllBytes(filePath);
            return fileParser.parse(filename, new java.io.ByteArrayInputStream(rawBytes));
        } catch (Exception e) {
            log.warn("Failed to reload file {} from disk: {}", cached.fileId(), e.getMessage());
            return null;
        }
    }

    private static UUID uuidOf(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(sessionId.getBytes(StandardCharsets.UTF_8));
        }
    }

    private Path sessionDir(String sessionId) {
        // Sanitize sessionId to prevent path traversal
        String safe = sessionId.replaceAll("[^a-zA-Z0-9_-]", "");
        return uploadDir.resolve(safe);
    }
}
