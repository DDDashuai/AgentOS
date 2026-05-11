package com.agentos.core.tools;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers database schema at startup by querying PostgreSQL information_schema.
 * Uses {@link PostConstruct} so discovery runs after Flyway migrations complete.
 * Injected into PromptOrchestrator so the LLM always knows the current schema.
 */
@Component
public class DatabaseSchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaProvider.class);

    private final DataSource dataSource;
    private String schemaDescription;

    public DatabaseSchemaProvider(DataSource dataSource) {
        this.dataSource = dataSource;
        this.schemaDescription = "(Discovering schema...)";
    }

    @PostConstruct
    public void init() {
        this.schemaDescription = discoverSchema();
    }

    public String getSchemaDescription() {
        return schemaDescription;
    }

    private String discoverSchema() {
        try (Connection conn = dataSource.getConnection()) {
            Map<String, List<String[]>> schema = new LinkedHashMap<>();

            try (Statement stmt = conn.createStatement();
                 ResultSet tables = stmt.executeQuery(
                     "SELECT table_name FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' " +
                     "ORDER BY table_name")) {

                List<String> tableNames = new ArrayList<>();
                while (tables.next()) {
                    tableNames.add(tables.getString("table_name"));
                }

                if (tableNames.isEmpty()) {
                    return "(No tables found in public schema)";
                }

                for (String table : tableNames) {
                    List<String[]> columns = new ArrayList<>();
                    try (Statement colStmt = conn.createStatement();
                         ResultSet cols = colStmt.executeQuery(
                             "SELECT column_name, data_type FROM information_schema.columns " +
                             "WHERE table_schema = 'public' AND table_name = '" + table + "' " +
                             "ORDER BY ordinal_position")) {
                        while (cols.next()) {
                            columns.add(new String[]{
                                cols.getString("column_name"),
                                cols.getString("data_type")
                            });
                        }
                    }
                    schema.put(table, columns);
                }
            }

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
