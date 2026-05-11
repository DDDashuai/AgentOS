package com.agentos.core.tools.impl;

import com.agentos.core.knowledge.KnowledgeService;
import com.agentos.core.tool.ExecutableTool;
import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchKnowledgeTool implements ExecutableTool {

    private static final Logger log = LoggerFactory.getLogger(SearchKnowledgeTool.class);
    private static final int DEFAULT_TOP_K = 5;

    private final KnowledgeService knowledgeService;

    public SearchKnowledgeTool(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String getName() {
        return "search_knowledge";
    }

    @Override
    public boolean isConcurrencySafe() {
        return true;
    }

    @Override
    public boolean isDestructive() {
        return false;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        String query = request.args().get("query");
        if (query == null || query.isBlank()) {
            return new ToolExecutionResult(getName(), false,
                    "Missing 'query' argument", 0, request.toolCallId());
        }

        int topK = DEFAULT_TOP_K;
        String topKStr = request.args().get("topK");
        if (topKStr != null && !topKStr.isBlank()) {
            try {
                topK = Integer.parseInt(topKStr);
            } catch (NumberFormatException e) {
                // ignore, use default
            }
        }

        long start = System.currentTimeMillis();
        try {
            List<KnowledgeService.SearchResult> results = knowledgeService.search(query, topK);
            String output = formatResults(results);
            long elapsed = System.currentTimeMillis() - start;
            return new ToolExecutionResult(getName(), true, output, elapsed, request.toolCallId());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Knowledge search failed: {}", e.getMessage(), e);
            return new ToolExecutionResult(getName(), false,
                    "Search failed: " + e.getMessage(), elapsed, request.toolCallId());
        }
    }

    private String formatResults(List<KnowledgeService.SearchResult> results) {
        if (results.isEmpty()) {
            return "No relevant knowledge found.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Knowledge search results:\n\n");
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append("[").append(i + 1).append("] (score: ")
                    .append(String.format("%.3f", r.score()))
                    .append(") [source: ").append(r.documentName())
                    .append("]\n").append(r.chunkText()).append("\n\n");
        }
        return sb.toString();
    }
}
