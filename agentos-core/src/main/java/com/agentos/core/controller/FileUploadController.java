package com.agentos.core.controller;

import com.agentos.core.entity.ChatSessionEntity;
import com.agentos.core.file.FileParserService;
import com.agentos.core.file.FileStorageService;
import com.agentos.core.file.UploadedFile;
import com.agentos.core.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);
    private static final Set<String> ALLOWED_TYPES = Set.of("csv", "xlsx", "pdf");

    private final FileParserService fileParser;
    private final FileStorageService fileStorage;
    private final ChatSessionRepository chatSessionRepository;
    private final long maxFileSize;

    public FileUploadController(FileParserService fileParser, FileStorageService fileStorage,
                                ChatSessionRepository chatSessionRepository,
                                @Value("${agentos.upload.max-file-size:10485760}") long maxFileSize) {
        this.fileParser = fileParser;
        this.fileStorage = fileStorage;
        this.chatSessionRepository = chatSessionRepository;
        this.maxFileSize = maxFileSize;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) String sessionId) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "File is empty"));
        }

        if (file.getSize() > maxFileSize) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "File exceeds max size of " + (maxFileSize / 1048576) + " MB"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid filename"));
        }

        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Unsupported file type: ." + ext + ". Allowed: " + ALLOWED_TYPES));
        }

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        // Ensure a chat_session record exists for this sessionId (FK requirement)
        ensureSession(sessionId);

        try {
            byte[] rawBytes = file.getBytes();
            UploadedFile parsed = fileParser.parse(originalName, new java.io.ByteArrayInputStream(rawBytes));
            fileStorage.store(sessionId, parsed, rawBytes);

            log.info("[{}] Uploaded file: {} ({} rows, {})",
                    sessionId, originalName, parsed.totalRowCount(), parsed.fileType());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "sessionId", sessionId,
                    "fileId", parsed.fileId(),
                    "fileName", originalName,
                    "fileType", parsed.fileType(),
                    "rowCount", parsed.totalRowCount(),
                    "headers", parsed.headers(),
                    "preview", parsed.previewRows()
            ));
        } catch (Exception e) {
            log.error("Failed to parse uploaded file: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to parse file: " + e.getMessage()));
        }
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> listFiles(@PathVariable String sessionId) {
        List<UploadedFile> files = fileStorage.list(sessionId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sessionId", sessionId,
                "files", files.stream().map(f -> Map.of(
                        "fileId", f.fileId(),
                        "fileName", f.originalFilename(),
                        "fileType", f.fileType(),
                        "rowCount", f.totalRowCount(),
                        "headers", f.headers()
                )).collect(Collectors.toList())
        ));
    }

    private void ensureSession(String sessionId) {
        UUID uuid = toSessionUuid(sessionId);
        chatSessionRepository.findById(uuid).orElseGet(() ->
                chatSessionRepository.save(new ChatSessionEntity(uuid, "File upload session")));
    }

    private static UUID toSessionUuid(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
