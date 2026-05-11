package com.agentos.core.tools.impl;

import com.agentos.core.tool.ExecutableTool;
import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class FileExportTool implements ExecutableTool {

    private static final Logger log = LoggerFactory.getLogger(FileExportTool.class);

    private static final Path EXPORT_DIR = Path.of("/tmp/agentos");

    private final ObjectMapper objectMapper;

    public FileExportTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "file_export";
    }

    @Override
    public boolean isConcurrencySafe() {
        return false; // sequential writes to filesystem
    }

    @Override
    public boolean isDestructive() {
        return true; // writes to local filesystem — triggers HumanApprovalInterceptor
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        Map<String, String> args = request.args();
        String dataJson = args.get("data");
        String filename = args.get("filename");

        if (dataJson == null || filename == null) {
            return new ToolExecutionResult(getName(), false,
                    "Missing required arguments: 'data' and 'filename'", 0, request.toolCallId());
        }

        long start = System.currentTimeMillis();
        try {
            Files.createDirectories(EXPORT_DIR);
            Path outputPath = EXPORT_DIR.resolve(filename);

            JsonNode data = objectMapper.readTree(dataJson);
            if (!data.isArray() || data.isEmpty()) {
                return new ToolExecutionResult(getName(), false,
                        "'data' must be a non-empty JSON array", 0, request.toolCallId());
            }

            try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath.toFile()))) {
                // Extract headers from the first object
                JsonNode first = data.get(0);
                List<String> headers = new ArrayList<>();
                first.fieldNames().forEachRemaining(headers::add);
                writer.println(String.join(",", headers));

                // Write data rows
                for (JsonNode row : data) {
                    List<String> values = new ArrayList<>();
                    for (String header : headers) {
                        JsonNode val = row.get(header);
                        if (val == null || val.isNull()) {
                            values.add("");
                        } else {
                            String s = val.asText().replace("\"", "\"\"");
                            values.add(s.contains(",") || s.contains("\"") ? "\"" + s + "\"" : s);
                        }
                    }
                    writer.println(String.join(",", values));
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            String result = "Exported " + data.size() + " rows to " + outputPath.toAbsolutePath();
            log.info(result);
            return new ToolExecutionResult(getName(), true, result, elapsed, request.toolCallId());

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("File export failed: {}", e.getMessage());
            return new ToolExecutionResult(getName(), false,
                    "Export failed: " + e.getMessage(), elapsed, request.toolCallId());
        }
    }
}
