package com.agentos.core.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for knowledge base operations.
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private static final int MAX_INGEST_SIZE = 1_048_576; // 1MB

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /** Request body for ingestion. */
    public record IngestRequest(String text, String documentName) {}

    /** Request body for search. */
    public record SearchRequest(String query, Integer topK) {}

    /**
     * Ingest a document into the knowledge base.
     */
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody IngestRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "text must not be empty"));
        }
        if (request.text().length() > MAX_INGEST_SIZE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "text exceeds maximum size of 1MB"));
        }
        String docName = request.documentName() != null ? request.documentName() : "unnamed";

        try {
            int chunkCount = knowledgeService.ingest(request.text(), docName);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "documentName", docName,
                    "chunkCount", chunkCount));
        } catch (Exception e) {
            log.error("Ingestion failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "error", e.getMessage()));
        }
    }

    /**
     * Search the knowledge base.
     */
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody SearchRequest request) {
        if (request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "error", "query must not be empty"));
        }

        int topK = request.topK() != null ? request.topK() : 5;

        try {
            List<KnowledgeService.SearchResult> results = knowledgeService.search(request.query(), topK);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "results", results.stream()
                            .map(r -> Map.of(
                                    "chunkText", r.chunkText(),
                                    "documentName", r.documentName(),
                                    "score", r.score()))
                            .toList()));
        } catch (Exception e) {
            log.error("Search failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "error", e.getMessage()));
        }
    }

    /**
     * Get knowledge base statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        long totalChunks = knowledgeService.count();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "totalChunks", totalChunks));
    }
}
