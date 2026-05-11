package com.agentos.core.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * HTTP client for the Python MLX embedding server.
 * <p>
 * Calls the embed server at {@code POST /embed} with a list of texts and
 * receives float vectors in return.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String embedUrl;

    public EmbeddingService(
            @Value("${agentos.knowledge.embedding-server-url}") String serverUrl,
            RestTemplateBuilder builder,
            ObjectMapper objectMapper) {
        this.embedUrl = serverUrl + "/embed";
        this.objectMapper = objectMapper;
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
    }

    /**
     * Compute embeddings for a batch of texts.
     *
     * @param texts list of texts to embed (must not be null)
     * @param prefix optional prefix to prepend (e.g. "passage: " for documents, "query: " for queries)
     * @return list of embedding vectors, same order as input
     * @throws EmbeddingException if the server call fails
     */
    public List<double[]> embed(List<String> texts, String prefix) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        var prefixedTexts = prefix != null && !prefix.isEmpty()
                ? texts.stream().map(t -> prefix + t).toList()
                : texts;

        try {
            String requestJson = objectMapper.writeValueAsString(
                    Collections.singletonMap("texts", prefixedTexts));
            String responseJson = restTemplate.postForObject(embedUrl, requestJson, String.class);

            if (responseJson == null) {
                throw new EmbeddingException("Empty response from embedding server");
            }

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode embeddings = root.get("embeddings");
            if (embeddings == null || !embeddings.isArray()) {
                throw new EmbeddingException("Unexpected response format: missing 'embeddings' array");
            }

            return parseEmbeddings(embeddings);

        } catch (Exception e) {
            String msg = "Embedding request failed: " + e.getMessage();
            log.error(msg);
            throw new EmbeddingException(msg, e);
        }
    }

    /**
     * Compute embeddings for a batch of texts without a prefix.
     */
    public List<double[]> embed(List<String> texts) {
        return embed(texts, null);
    }

    /**
     * Compute embedding for a single text with an optional prefix.
     */
    public double[] embed(String text, String prefix) {
        List<double[]> results = embed(List.of(text), prefix);
        if (results.isEmpty()) {
            throw new EmbeddingException("Embedding returned empty result");
        }
        return results.getFirst();
    }

    /**
     * Compute embedding for a single text without a prefix.
     */
    public double[] embed(String text) {
        return embed(text, null);
    }

    /**
     * Compute cosine similarity between two vectors.
     * <p>
     * Returns a value in [-1, 1] where 1 = identical direction.
     */
    public static double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector dimension mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    /** Unchecked exception thrown by embedding operations. */
    public static class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message) {
            super(message);
        }

        public EmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private List<double[]> parseEmbeddings(JsonNode arr) {
        return java.util.stream.StreamSupport.stream(arr.spliterator(), false)
                .map(this::parseVector)
                .toList();
    }

    private double[] parseVector(JsonNode vec) {
        double[] result = new double[vec.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = vec.get(i).asDouble();
        }
        return result;
    }
}
