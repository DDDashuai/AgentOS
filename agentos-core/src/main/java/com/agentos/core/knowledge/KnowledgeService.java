package com.agentos.core.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * Orchestrator for the RAG knowledge base.
 * <p>
 * Handles document ingestion (chunk → embed → store) and semantic search
 * (embed query → cosine similarity over all stored chunks).
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private static final double MIN_SCORE = 0.0;
    private static final int DEFAULT_TOP_K = 5;

    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeService(TextSplitter textSplitter,
                            EmbeddingService embeddingService,
                            JdbcTemplate jdbcTemplate) {
        this.textSplitter = textSplitter;
        this.embeddingService = embeddingService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** A chunk stored in the database. */
    public record KnowledgeChunk(
            UUID id,
            String documentName,
            int chunkIndex,
            String chunkText,
            double[] embedding,
            int tokenCount,
            Instant createdAt
    ) {}

    /** A search result with relevance score. */
    public record SearchResult(String chunkText, String documentName, double score) {}

    /**
     * Ingest a document into the knowledge base.
     * <p>
     * Splits the text into chunks, computes embeddings, and stores them.
     * If a document with the same name already exists, it is replaced.
     *
     * @param text         the document text
     * @param documentName a name/label for the source document
     * @return the number of chunks ingested
     */
    @Transactional
    public int ingest(String text, String documentName) {
        if (text == null || text.isBlank()) {
            log.warn("Ignoring empty document: {}", documentName);
            return 0;
        }

        // Remove existing chunks for this document (re-ingestion)
        deleteByDocument(documentName);

        // Split into chunks
        List<TextSplitter.ChunkResult> chunks = textSplitter.split(text, documentName);
        if (chunks.isEmpty()) {
            log.warn("No chunks produced for document: {}", documentName);
            return 0;
        }

        // Extract chunk texts for batch embedding
        List<String> chunkTexts = chunks.stream()
                .map(TextSplitter.ChunkResult::text)
                .toList();

        // Compute embeddings in batch (E5-style "passage: " prefix)
        List<double[]> embeddings = embeddingService.embed(chunkTexts, "passage: ");

        // Batch insert into database
        var now = java.sql.Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate(
                "INSERT INTO knowledge_chunks (id, document_name, chunk_index, chunk_text, embedding, token_count, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                chunks,
                chunks.size(),
                (PreparedStatement ps, TextSplitter.ChunkResult chunk) -> {
                    int idx = chunks.indexOf(chunk);
                    double[] emb = idx < embeddings.size() ? embeddings.get(idx) : new double[0];
                    ps.setObject(1, UUID.randomUUID());
                    ps.setString(2, documentName);
                    ps.setInt(3, chunk.index());
                    ps.setString(4, chunk.text());
                    try {
                        ps.setArray(5, ps.getConnection().createArrayOf("float8", toDoubleArray(emb)));
                    } catch (SQLException e) {
                        throw new RuntimeException("Failed to create array for embedding", e);
                    }
                    ps.setInt(6, chunk.tokenCount());
                    ps.setObject(7, now);
                });

        log.info("Ingested {} chunks from document: {}", chunks.size(), documentName);
        return chunks.size();
    }

    /**
     * Search the knowledge base for chunks semantically similar to the query.
     *
     * @param query the search query
     * @param topK  maximum number of results
     * @return list of search results sorted by relevance (highest first)
     */
    public List<SearchResult> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (topK <= 0) {
            topK = DEFAULT_TOP_K;
        }

        // Compute query embedding (E5-style "query: " prefix)
        double[] queryEmbedding = embeddingService.embed(query, "query: ");

        // Load all chunks from DB and compute similarity
        List<SearchResult> scored = jdbcTemplate.query(
                "SELECT chunk_text, document_name, embedding FROM knowledge_chunks",
                (rs, rowNum) -> {
                    String chunkText = rs.getString("chunk_text");
                    String docName = rs.getString("document_name");
                    double[] chunkEmbedding = toDoubleArray(rs.getArray("embedding"));
                    double score = EmbeddingService.cosineSimilarity(queryEmbedding, chunkEmbedding);
                    return new SearchResult(chunkText, docName, score);
                });

        // Filter low-scoring results and sort by score descending
        return scored.stream()
                .filter(r -> r.score() >= MIN_SCORE)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(topK)
                .toList();
    }

    /**
     * Search with default top-K.
     */
    public List<SearchResult> search(String query) {
        return search(query, DEFAULT_TOP_K);
    }

    /**
     * Count total chunks in the knowledge base.
     */
    public long count() {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM knowledge_chunks", Long.class);
        return result != null ? result : 0;
    }

    /**
     * Delete all chunks for a given document.
     */
    public void deleteByDocument(String documentName) {
        jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE document_name = ?", documentName);
    }

    /** Convert a {@code double[]} to a {@code Double[]} for JDBC array creation. */
    private static Double[] toDoubleArray(double[] arr) {
        Double[] result = new Double[arr.length];
        for (int i = 0; i < arr.length; i++) {
            result[i] = arr[i];
        }
        return result;
    }

    /** Convert a JDBC {@code java.sql.Array} to a {@code double[]}. */
    private static double[] toDoubleArray(java.sql.Array array) {
        try {
            if (array == null) return new double[0];
            Object[] raw = (Object[]) array.getArray();
            double[] result = new double[raw.length];
            for (int i = 0; i < raw.length; i++) {
                result[i] = ((Number) raw[i]).doubleValue();
            }
            return result;
        } catch (SQLException e) {
            log.warn("Failed to read embedding array", e);
            return new double[0];
        }
    }
}
