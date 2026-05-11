package com.agentos.core.knowledge;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive character text splitter.
 * <p>
 * Splits text into chunks by trying separators in order of semantic priority:
 * {@code "\n\n"} (paragraphs) → {@code "\n"} (lines) → {@code "."} (sentences)
 * → {@code " "} (words) → character-level fallback.
 */
@Component
public class TextSplitter {

    private static final List<String> SEPARATORS = List.of("\n\n", "\n", ".", " ", "");

    private final int chunkSize;
    private final int chunkOverlap;
    private final int maxChunks;

    /** Spring-managed constructor — values come from {@code application.yml}. */
    @Autowired
    public TextSplitter(
            @Value("${agentos.knowledge.chunk.chunk-size}") Integer chunkSize,
            @Value("${agentos.knowledge.chunk.chunk-overlap}") Integer chunkOverlap,
            @Value("${agentos.knowledge.chunk.max-chunks-per-document}") Integer maxChunks) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.maxChunks = maxChunks;
    }

    /** Direct constructor (for testing). */
    public TextSplitter(int chunkSize, int chunkOverlap, int maxChunks) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.maxChunks = maxChunks;
    }

    /**
     * Split result for a single chunk.
     *
     * @param text       the chunk text
     * @param index      the 0-based index within the document
     * @param tokenCount approximate token count (characters / 4)
     */
    public record ChunkResult(String text, int index, int tokenCount) {}

    /**
     * Split the given text into chunks.
     *
     * @param text         the text to split
     * @param documentName name of the source document (used only for logging)
     * @return list of chunk results, or empty list if text is null/blank
     */
    public List<ChunkResult> split(String text, String documentName) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<ChunkResult> results = new ArrayList<>();
        splitRecursive(text.strip(), 0, results);
        return List.copyOf(results);
    }

    private void splitRecursive(String text, int depth, List<ChunkResult> results) {
        if (text == null || text.isBlank() || results.size() >= maxChunks) {
            return;
        }

        // Base case: text fits in a single chunk
        if (text.length() <= chunkSize) {
            results.add(new ChunkResult(text, results.size(), approximateTokens(text)));
            return;
        }

        // Try each separator, from largest semantic unit to smallest
        for (String separator : SEPARATORS) {
            if (separator.isEmpty()) {
                // Character-level split: split at chunkSize boundary
                String chunk = text.substring(0, chunkSize);
                results.add(new ChunkResult(chunk, results.size(), approximateTokens(chunk)));
                int overlapStart = Math.max(0, chunkSize - chunkOverlap);
                splitRecursive(text.substring(overlapStart), depth + 1, results);
                return;
            }

            int splitIdx = findLastSeparatorWithin(text, separator, chunkSize);
            if (splitIdx > 0) {
                String chunk = text.substring(0, splitIdx);
                String remainder = text.substring(splitIdx + separator.length());
                results.add(new ChunkResult(chunk, results.size(), approximateTokens(chunk)));
                int overlapStart = Math.max(0, splitIdx - chunkOverlap);
                splitRecursive(text.substring(overlapStart), depth + 1, results);
                return;
            }
        }
    }

    /**
     * Find the last occurrence of {@code separator} within the first {@code limit}
     * characters of {@code text}. Returns -1 if not found.
     */
    static int findLastSeparatorWithin(String text, String separator, int limit) {
        int searchEnd = Math.min(limit, text.length());
        String searchRegion = text.substring(0, searchEnd);
        return searchRegion.lastIndexOf(separator);
    }

    /** Approximate token count as characters / 4. */
    static int approximateTokens(String text) {
        if (text == null || text.isEmpty()) return 1;
        return Math.max(1, text.length() / 4);
    }
}
