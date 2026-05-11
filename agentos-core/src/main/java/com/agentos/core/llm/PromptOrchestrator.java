package com.agentos.core.llm;

import com.agentos.core.tools.DatabaseSchemaProvider;
import com.agentos.core.tool.ToolDefinition;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cache-aware prompt orchestrator.
 * <p>
 * The system prompt is built once at startup (static, containing tool schemas
 * and discovered database schema) and never changes. Dynamic user history is
 * appended separately. This strict separation maximizes API prompt caching hit
 * rates on the MLX server.
 */
@Component
public class PromptOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PromptOrchestrator.class);

    private final String systemPrompt;
    private final List<ToolSpecification> toolSpecifications;
    private final String databaseSchema;

    public PromptOrchestrator(List<ToolDefinition> tools, DatabaseSchemaProvider schemaProvider) {
        this.databaseSchema = schemaProvider.getSchemaDescription();
        this.toolSpecifications = tools.stream()
                .map(this::toToolSpecification)
                .toList();
        this.systemPrompt = buildSystemPrompt(tools);
        log.info("Built system prompt ({} chars) for {} tools", systemPrompt.length(), tools.size());
    }

    /** The static system prompt — always identical, ideal for prompt caching. */
    public String systemPrompt() {
        return systemPrompt;
    }

    /** Tool specifications to pass to the LLM for function-calling. */
    public List<ToolSpecification> toolSpecifications() {
        return toolSpecifications;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String buildSystemPrompt(List<ToolDefinition> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are AgentOS, an AI agent. You have access to tools you can call to help the user.\n\n");

        sb.append("AVAILABLE TOOLS:\n");
        for (ToolDefinition tool : tools) {
            sb.append("- ").append(tool.getName()).append(": ");
            sb.append(buildToolDescription(tool)).append("\n");
        }

        sb.append("\nDATABASE SCHEMA:\n")
                .append(databaseSchema).append("\n")
                .append("\nHOW TO USE TOOLS:\n")
                .append("When you need to use a tool, output a tool call on its own line in EXACTLY this format:\n")
                .append("[TOOL_CALL] {\"name\": \"tool_name\", \"arguments\": {\"param1\": \"value1\"}} [/TOOL_CALL]\n\n")
                .append("Example: To search the knowledge base, output:\n")
                .append("[TOOL_CALL] {\"name\": \"search_knowledge\", \"arguments\": {\"query\": \"PostgreSQL tuning\"}} [/TOOL_CALL]\n\n")
                .append("RULES:\n")
                .append("1. Always use the EXACT table and column names from DATABASE SCHEMA above.\n")
                .append("2. When you call database_query, it returns a JSON result. Use the EXACT data from that result — do not invent or change values.\n")
                .append("3. To chain tools: call database_query or search_knowledge first, wait for the result, then call the next tool.\n")
                .append("4. Only call ONE tool at a time. Wait for the result before calling another tool.\n")
                .append("5. Always output valid JSON: use double quotes (\") not single quotes (') for JSON strings.\n")
                .append("6. Before answering knowledge questions, always call search_knowledge first.\n")
                .append("7. When you have enough information, provide your final answer (without any [TOOL_CALL]).\n");
        return sb.toString();
    }

    private ToolSpecification toToolSpecification(ToolDefinition tool) {
        return ToolSpecification.builder()
                .name(tool.getName())
                .description(buildToolDescription(tool))
                .parameters(buildParameters(tool))
                .build();
    }

    /** Builds a JSON Schema describing the tool's expected arguments. */
    private JsonObjectSchema buildParameters(ToolDefinition tool) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();

        switch (tool.getName()) {
            case "database_query" -> {
                builder.addStringProperty("query", "SQL SELECT query to execute on the database");
                builder.required("query");
            }
            case "data_visualization" -> {
                builder.addStringProperty("chartType", "Chart type: bar, line, pie, or scatter");
                builder.addStringProperty("data", "JSON array of data objects, e.g. [{\"name\":\"Q1\",\"value\":100}]");
                builder.required("chartType", "data");
            }
            case "file_export" -> {
                builder.addStringProperty("data", "JSON array of data rows to export");
                builder.addStringProperty("filename", "Output filename (e.g. report.csv)");
                builder.required("data", "filename");
            }
            case "bash_execution" -> {
                builder.addStringProperty("command", "The bash command to execute (e.g. 'ls -la', 'echo hello').");
                builder.required("command");
            }
            case "search_knowledge" -> {
                builder.addStringProperty("query", "The search query to find relevant knowledge");
                builder.required("query");
            }
            case "query_uploaded_data" -> {
                builder.addStringProperty("fileId",
                        "The fileId or original filename of the uploaded file.");
                builder.required("fileId");
            }
            default -> {}
        }

        return builder.build();
    }

    private String buildToolDescription(ToolDefinition tool) {
        return switch (tool.getName()) {
            case "database_query" ->
                "Execute a SQL SELECT query on the database. Schema:\n" + databaseSchema
                    + "\nParameters: {\"query\": \"SQL query string\"}";
            case "data_visualization" ->
                "Generate an ECharts chart from REAL data. Parameters: {\"chartType\": \"bar|line|pie|scatter\", \"data\": \"JSON array of data objects\"}";
            case "file_export" ->
                "Export data to a CSV file. DESTRUCTIVE — requires human approval. Parameters: {\"data\": \"JSON array\", \"filename\": \"output.csv\"}";
            case "bash_execution" ->
                "Execute a bash command. DESTRUCTIVE — requires human approval. Parameters: {\"command\": \"shell command\"}";
            case "local_search" ->
                "Search local files and directories for content.";
            case "query_uploaded_data" ->
                "Query data from an uploaded file (CSV, XLSX, or PDF). Returns JSON rows. Parameters: {\"fileId\": \"file-id\"}";
            case "search_knowledge" ->
                "Search the knowledge base for information relevant to the user's query. Returns text chunks with relevance scores. Parameters: {\"query\": \"search query\"}";
            default ->
                tool.getName() + " tool.";
        };
    }
}
