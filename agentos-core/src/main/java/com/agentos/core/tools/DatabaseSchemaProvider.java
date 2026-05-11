package com.agentos.core.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers database schema at startup by querying SQLite metadata.
 * Injected into PromptOrchestrator so the LLM always knows the current schema.
 */
@Component
public class DatabaseSchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaProvider.class);

    private final String schemaDescription;

    public DatabaseSchemaProvider() {
        this.schemaDescription = discoverSchema();
    }

    public String getSchemaDescription() {
        return schemaDescription;
    }

    private String discoverSchema() {
        String dbUrl = "jdbc:sqlite:agentos_data.db";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {

            // Discover all tables
            ResultSet tables = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
            List<String> tableNames = new ArrayList<>();
            while (tables.next()) {
                tableNames.add(tables.getString("name"));
            }
            tables.close();

            if (tableNames.isEmpty()) {
                return "(No tables found in database)";
            }

            // Discover columns per table
            Map<String, List<String[]>> schema = new LinkedHashMap<>();
            for (String table : tableNames) {
                List<String[]> columns = new ArrayList<>();
                try (Statement colStmt = conn.createStatement();
                     ResultSet cols = colStmt.executeQuery("PRAGMA table_info(\"" + table + "\")")) {
                    while (cols.next()) {
                        columns.add(new String[]{
                            cols.getString("name"),
                            cols.getString("type")
                        });
                    }
                }
                schema.put(table, columns);
            }

            // Build description string
            StringBuilder sb = new StringBuilder();
            for (var entry : schema.entrySet()) {
                sb.append("  Table: ").append(entry.getKey()).append("\n");
                for (String[] col : entry.getValue()) {
                    sb.append("    ").append(col[0]).append(" ").append(col[1]).append("\n");
                }
            }
            log.info("Discovered database schema:\n{}", sb);
            return sb.toString();

        } catch (Exception e) {
            log.warn("Failed to discover database schema: {}", e.getMessage());
            return "(Could not discover database schema: " + e.getMessage() + ")";
        }
    }
}
