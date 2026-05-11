package com.agentos.core.tools.impl;

import com.agentos.core.tool.ExecutableTool;
import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

@Component
public class DataVisualizationTool implements ExecutableTool {

    private static final Logger log = LoggerFactory.getLogger(DataVisualizationTool.class);

    private static final java.util.Set<String> SUPPORTED_CHART_TYPES =
            java.util.Set.of("line", "bar", "pie", "scatter");

    private final ObjectMapper objectMapper;

    public DataVisualizationTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "data_visualization";
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
        String chartType = args.get("chartType");
        String dataJson = args.get("data");

        if (chartType == null || dataJson == null) {
            return new ToolExecutionResult(getName(), false,
                    "Missing required arguments: 'chartType' and 'data'", 0, request.toolCallId());
        }
        if (!SUPPORTED_CHART_TYPES.contains(chartType)) {
            return new ToolExecutionResult(getName(), false,
                    "Unsupported chart type: " + chartType + ". Supported: " + SUPPORTED_CHART_TYPES,
                    0, request.toolCallId());
        }

        long start = System.currentTimeMillis();
        try {
            JsonNode data = objectMapper.readTree(dataJson);
            if (!data.isArray() || data.isEmpty()) {
                return new ToolExecutionResult(getName(), false,
                        "'data' must be a non-empty JSON array", 0, request.toolCallId());
            }

            String config = buildEChartsConfig(chartType, (ArrayNode) data);
            long elapsed = System.currentTimeMillis() - start;
            return new ToolExecutionResult(getName(), true, config, elapsed, request.toolCallId());

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Data visualization failed: {}", e.getMessage());
            return new ToolExecutionResult(getName(), false,
                    "Failed to build chart config: " + e.getMessage(), elapsed, request.toolCallId());
        }
    }

    private String buildEChartsConfig(String chartType, ArrayNode data) throws Exception {
        ObjectNode option = objectMapper.createObjectNode();

        // Detect chart structure from first data object
        JsonNode first = data.get(0);

        if ("pie".equals(chartType)) {
            option.put("title", "Pie Chart");
            ArrayNode seriesData = objectMapper.createArrayNode();
            for (JsonNode item : data) {
                ObjectNode point = objectMapper.createObjectNode();
                Iterator<String> fields = item.fieldNames();
                String nameField = fields.next();
                String valueField = fields.next();
                point.put("name", item.get(nameField).asText());
                point.put("value", item.get(valueField).asDouble());
                seriesData.add(point);
            }
            ObjectNode series = objectMapper.createObjectNode();
            series.put("type", "pie");
            series.set("data", seriesData);
            ArrayNode seriesArr = objectMapper.createArrayNode();
            seriesArr.add(series);
            option.set("series", seriesArr);
        } else {
            // Bar, line, scatter — extract category and value columns
            Iterator<String> fields = first.fieldNames();
            String categoryField = fields.next();
            String valueField = fields.next();

            ArrayNode categories = objectMapper.createArrayNode();
            ArrayNode values = objectMapper.createArrayNode();
            for (JsonNode item : data) {
                categories.add(item.get(categoryField).asText());
                values.add(item.get(valueField).asDouble());
            }

            ObjectNode xAxis = objectMapper.createObjectNode();
            xAxis.put("type", "category");
            xAxis.set("data", categories);
            option.set("xAxis", xAxis);

            ObjectNode yAxis = objectMapper.createObjectNode();
            yAxis.put("type", "value");
            option.set("yAxis", yAxis);

            ObjectNode series = objectMapper.createObjectNode();
            series.put("type", chartType);
            series.set("data", values);
            ArrayNode seriesArr = objectMapper.createArrayNode();
            seriesArr.add(series);
            option.set("series", seriesArr);
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("chartType", chartType);
        result.set("option", option);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }
}
