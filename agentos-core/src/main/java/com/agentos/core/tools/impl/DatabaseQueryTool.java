package com.agentos.core.tools.impl;

import com.agentos.core.tool.ExecutableTool;
import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;

@Component
public class DatabaseQueryTool implements ExecutableTool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryTool.class);

    private final ObjectMapper objectMapper;
    private final DataSource dataSource;

    public DatabaseQueryTool(ObjectMapper objectMapper, DataSource dataSource) {
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
    }

    @Override
    public String getName() {
        return "database_query";
    }

    @Override
    public boolean isConcurrencySafe() {
        return true; // read-only SELECT
    }

    @Override
    public boolean isDestructive() {
        return false;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        String sql = request.args().get("query");
        if (sql == null || sql.isBlank()) {
            return new ToolExecutionResult(getName(), false, "Missing 'query' argument", 0, request.toolCallId());
        }

        // Reject non-SELECT statements for safety
        if (!sql.trim().toUpperCase().startsWith("SELECT")) {
            return new ToolExecutionResult(getName(), false,
                    "Only SELECT queries are allowed", 0, request.toolCallId());
        }

        long start = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            String json = resultSetToJson(rs);
            long elapsed = System.currentTimeMillis() - start;
            return new ToolExecutionResult(getName(), true, json, elapsed, request.toolCallId());

        } catch (SQLException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Database query failed: {}", e.getMessage());
            return new ToolExecutionResult(getName(), false,
                    "SQL error: " + e.getMessage(), elapsed, request.toolCallId());
        }
    }

    private String resultSetToJson(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        ArrayNode rows = objectMapper.createArrayNode();

        while (rs.next()) {
            ObjectNode row = objectMapper.createObjectNode();
            for (int i = 1; i <= columnCount; i++) {
                String colName = meta.getColumnLabel(i);
                Object value = rs.getObject(i);
                if (value == null) {
                    row.putNull(colName);
                } else if (value instanceof Number n) {
                    row.put(colName, n.doubleValue());
                } else {
                    row.put(colName, value.toString());
                }
            }
            rows.add(row);
        }

        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("rows", rows);
        wrapper.put("rowCount", rows.size());
        return wrapper.toString();
    }
}
