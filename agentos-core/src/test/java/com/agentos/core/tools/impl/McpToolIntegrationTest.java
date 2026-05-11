package com.agentos.core.tools.impl;

import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class McpToolIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A simple DataSource that creates a new SQLite connection each time. */
    private static DataSource sqliteDataSource(String url) {
        return new DataSource() {
            @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url); }
            @Override public Connection getConnection(String username, String password) { throw new UnsupportedOperationException(); }
            @Override public PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(PrintWriter out) {}
            @Override public void setLoginTimeout(int seconds) {}
            @Override public int getLoginTimeout() { return 0; }
            @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
            @Override public <T> T unwrap(Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }

    // ---- DatabaseQueryTool tests ----

    @Test
    void databaseQueryTool_ExecutesSelectAndReturnsJson(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("test.db");
        String dbUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();

        try (Connection conn = DriverManager.getConnection(dbUrl);
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users (id INTEGER, name TEXT, age INTEGER)");
            stmt.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
            stmt.execute("INSERT INTO users VALUES (2, 'Bob', 25)");
            stmt.execute("INSERT INTO users VALUES (3, 'Charlie', 35)");
        }

        DatabaseQueryTool tool = new DatabaseQueryTool(MAPPER, sqliteDataSource(dbUrl));
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("database_query", Map.of("query", "SELECT * FROM users ORDER BY id"), null));

        assertTrue(result.success(), "Query should succeed: " + result.output());
        JsonNode json = MAPPER.readTree(result.output());
        assertEquals(3, json.get("rowCount").asInt());
        assertEquals("Alice", json.get("rows").get(0).get("name").asText());
        assertEquals(25, json.get("rows").get(1).get("age").asInt());
    }

    @Test
    void databaseQueryTool_RejectsNonSelect() {
        DatabaseQueryTool tool = new DatabaseQueryTool(MAPPER, sqliteDataSource("jdbc:sqlite::memory:"));
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("database_query", Map.of("query", "DROP TABLE users"), null));

        assertFalse(result.success());
        assertTrue(result.output().contains("Only SELECT"));
    }

    @Test
    void databaseQueryTool_ReturnsErrorForInvalidSql(@TempDir Path tempDir) {
        DatabaseQueryTool tool = new DatabaseQueryTool(MAPPER,
                sqliteDataSource("jdbc:sqlite:" + tempDir.resolve("empty.db").toAbsolutePath()));
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("database_query", Map.of("query", "SELECT * FROM nonexistent"), null));

        assertFalse(result.success());
        assertTrue(result.output().contains("SQL error"));
    }

    @Test
    void databaseQueryTool_MissingQueryArg() {
        DatabaseQueryTool tool = new DatabaseQueryTool(MAPPER, sqliteDataSource("jdbc:sqlite::memory:"));
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("database_query", Map.of(), null));

        assertFalse(result.success());
        assertTrue(result.output().contains("Missing"));
    }

    // ---- DataVisualizationTool tests ----

    @Test
    void dataVisualizationTool_GeneratesBarChart() {
        DataVisualizationTool tool = new DataVisualizationTool(MAPPER);
        ToolExecutionResult result = tool.execute(new ToolExecutionRequest(
                "data_visualization",
                Map.of("chartType", "bar", "data", "[{\"month\":\"Jan\",\"sales\":100},{\"month\":\"Feb\",\"sales\":200}]"),
                null));

        assertTrue(result.success(), result.output());
        assertAll(
                () -> assertTrue(result.output().contains("\"chartType\" : \"bar\"")),
                () -> assertTrue(result.output().contains("\"type\" : \"bar\"")),
                () -> assertTrue(result.output().contains("Jan")),
                () -> assertTrue(result.output().contains("Feb")),
                () -> assertTrue(result.output().contains("100")),
                () -> assertTrue(result.output().contains("200"))
        );
    }

    @Test
    void dataVisualizationTool_GeneratesPieChart() {
        DataVisualizationTool tool = new DataVisualizationTool(MAPPER);
        ToolExecutionResult result = tool.execute(new ToolExecutionRequest(
                "data_visualization",
                Map.of("chartType", "pie", "data", "[{\"name\":\"Apples\",\"value\":30},{\"name\":\"Oranges\",\"value\":70}]"),
                null));

        assertTrue(result.success());
        assertTrue(result.output().contains("\"type\" : \"pie\""));
    }

    @Test
    void dataVisualizationTool_RejectsUnknownChartType() {
        DataVisualizationTool tool = new DataVisualizationTool(MAPPER);
        ToolExecutionResult result = tool.execute(new ToolExecutionRequest(
                "data_visualization",
                Map.of("chartType", "3d", "data", "[]"),
                null));

        assertFalse(result.success());
        assertTrue(result.output().contains("Unsupported"));
    }

    @Test
    void dataVisualizationTool_MissingArgs() {
        DataVisualizationTool tool = new DataVisualizationTool(MAPPER);
        ToolExecutionResult result = tool.execute(new ToolExecutionRequest(
                "data_visualization", Map.of(), null));

        assertFalse(result.success());
        assertTrue(result.output().contains("Missing"));
    }

    // ---- FileExportTool tests ----

    @Test
    void fileExportTool_WritesCsv() throws Exception {
        FileExportTool tool = new FileExportTool(MAPPER);
        String filename = "test_export_" + System.nanoTime() + ".csv";

        try {
            ToolExecutionResult result = tool.execute(new ToolExecutionRequest(
                    "file_export",
                    Map.of("data", "[{\"name\":\"Alice\",\"age\":30},{\"name\":\"Bob\",\"age\":25}]",
                            "filename", filename),
                    null));

            assertTrue(result.success(), result.output());
            assertTrue(result.output().contains(filename));

            Path exportPath = Path.of("/tmp/agentos", filename);
            assertTrue(Files.exists(exportPath));
            String content = Files.readString(exportPath);
            assertTrue(content.contains("name,age") || content.contains("age,name"));
            assertTrue(content.contains("Alice"));
            assertTrue(content.contains("30"));
        } finally {
            Path exportPath = Path.of("/tmp/agentos", filename);
            Files.deleteIfExists(exportPath);
        }
    }

    @Test
    void fileExportTool_MissingArgs() {
        FileExportTool tool = new FileExportTool(MAPPER);
        ToolExecutionResult result = tool.execute(new ToolExecutionRequest(
                "file_export", Map.of(), null));

        assertFalse(result.success());
        assertTrue(result.output().contains("Missing"));
    }

    @Test
    void fileExportTool_DestructiveFlag() {
        DatabaseQueryTool dbTool = new DatabaseQueryTool(MAPPER, sqliteDataSource("jdbc:sqlite::memory:"));
        FileExportTool fileTool = new FileExportTool(MAPPER);

        assertFalse(dbTool.isDestructive());
        assertTrue(fileTool.isDestructive());
        assertFalse(fileTool.isConcurrencySafe());
    }
}
