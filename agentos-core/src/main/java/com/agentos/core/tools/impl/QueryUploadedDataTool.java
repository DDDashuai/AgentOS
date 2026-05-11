package com.agentos.core.tools.impl;

import com.agentos.core.file.FileStorageService;
import com.agentos.core.file.UploadedFile;
import com.agentos.core.tool.ExecutableTool;
import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QueryUploadedDataTool implements ExecutableTool {

    private static final Logger log = LoggerFactory.getLogger(QueryUploadedDataTool.class);

    private final FileStorageService fileStorage;
    private final ObjectMapper objectMapper;

    public QueryUploadedDataTool(FileStorageService fileStorage, ObjectMapper objectMapper) {
        this.fileStorage = fileStorage;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "query_uploaded_data";
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
        Map<String, String> args = request.args();
        String fileId = args.get("fileId");

        if (fileId == null || fileId.isBlank()) {
            return new ToolExecutionResult(getName(), false,
                    "Missing 'fileId' argument. Use the fileId or original filename from the uploaded file.",
                    0, request.toolCallId());
        }

        long start = System.currentTimeMillis();
        String sessionId = request.sessionId();
        if (sessionId == null) {
            return new ToolExecutionResult(getName(), false,
                    "No session context available", 0, request.toolCallId());
        }

        UploadedFile file = fileStorage.get(sessionId, fileId);
        if (file == null) {
            return new ToolExecutionResult(getName(), false,
                    "No file found with id/name '" + fileId + "'."
                            + " Available files: " + fileStorage.list(sessionId).stream()
                            .map(f -> f.fileId() + " (" + f.originalFilename() + ")").toList(),
                    0, request.toolCallId());
        }

        // Return in same format as DatabaseQueryTool for data_visualization compatibility
        ArrayNode rows = objectMapper.createArrayNode();
        for (Map<String, String> row : file.rows()) {
            ObjectNode rowNode = objectMapper.createObjectNode();
            row.forEach(rowNode::put);
            rows.add(rowNode);
        }

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.put("rowCount", rows.size());
        wrapper.put("source", file.originalFilename());
        wrapper.put("fileId", file.fileId());

        long elapsed = System.currentTimeMillis() - start;
        return new ToolExecutionResult(getName(), true, wrapper.toString(), elapsed, request.toolCallId());
    }
}
