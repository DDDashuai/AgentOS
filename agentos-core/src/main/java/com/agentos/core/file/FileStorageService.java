package com.agentos.core.file;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-session in-memory file storage.
 * Data lives as long as the session exists (same lifecycle as AgentState).
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Map<String, Map<String, UploadedFile>> sessionFiles = new ConcurrentHashMap<>();

    public void store(String sessionId, UploadedFile file) {
        sessionFiles.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                .put(file.fileId(), file);
        log.info("[{}] Stored file: {} ({} rows)", sessionId, file.originalFilename(), file.totalRowCount());
    }

    public UploadedFile get(String sessionId, String fileIdOrName) {
        Map<String, UploadedFile> files = sessionFiles.get(sessionId);
        if (files == null) return null;
        UploadedFile f = files.get(fileIdOrName);
        if (f != null) return f;
        return files.values().stream()
                .filter(u -> u.originalFilename().equals(fileIdOrName))
                .findFirst().orElse(null);
    }

    public List<UploadedFile> list(String sessionId) {
        Map<String, UploadedFile> files = sessionFiles.get(sessionId);
        return files == null ? List.of() : List.copyOf(files.values());
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
        sessionFiles.remove(sessionId);
        log.info("[{}] Cleared uploaded files", sessionId);
    }
}
