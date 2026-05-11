package com.agentos.core.llm;

import com.agentos.core.state.AgentState;
import com.agentos.core.state.ChatMessage;
import com.agentos.core.file.FileStorageService;
import com.agentos.core.tools.DatabaseSchemaProvider;
import com.agentos.core.tool.ToolDefinition;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private final FileStorageService fileStorageService;

    public PromptOrchestrator(List<ToolDefinition> tools, DatabaseSchemaProvider schemaProvider,
                              FileStorageService fileStorageService) {
        this.databaseSchema = schemaProvider.getSchemaDescription();
        this.fileStorageService = fileStorageService;
        this.toolSpecifications = tools.stream()
                .map(this::toToolSpecification)
                .toList();
        this.systemPrompt = buildSystemPrompt(this.toolSpecifications);
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

    /**
     * Builds the full LangChain4j message list: [SystemMessage, ...history, ...userPrompt].
     * The system message is always first and always byte-identical, giving the MLX
     * server the best chance to serve the system prompt from its prefix cache.
     * Tool result messages use the original tool call IDs from the LLM.
     */
    public List<dev.langchain4j.data.message.ChatMessage> buildMessages(
            AgentState state, String userPrompt) {

        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        // 1. Static system prompt — cache anchor
        messages.add(new SystemMessage(systemPrompt));

        // 2. Conversation history (user ↔ assistant ↔ tool)
        for (ChatMessage msg : state.history()) {
            switch (msg.role()) {
                case "user" -> messages.add(new UserMessage(msg.content()));
                case "assistant" -> messages.add(new AiMessage(msg.content()));
                case "tool" -> {
                    int colonIdx = msg.content().indexOf(':');
                    if (colonIdx > 0) {
                        String toolName = msg.content().substring(0, colonIdx);
                        String result = msg.content().substring(colonIdx + 1);
                        String id = msg.toolCallId() != null ? msg.toolCallId() : "call_" + System.nanoTime();
                        messages.add(new ToolExecutionResultMessage(id, toolName, result));
                    } else {
                        messages.add(new ToolExecutionResultMessage("call_0", "unknown", msg.content()));
                    }
                }
                default -> log.warn("Unknown message role: {}", msg.role());
            }
        }

        // 3. Uploaded file context — injected before the user prompt
        String fileDesc = fileStorageService.buildDescription(state.sessionId());
        String augmentedPrompt = fileDesc.isEmpty() ? userPrompt
                : fileDesc + "\n[END OF UPLOADED FILE CONTEXT]\n---\n" + userPrompt;

        // 4. Current user prompt (augmented with file context if any)
        messages.add(new UserMessage(augmentedPrompt));

        return messages;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String buildSystemPrompt(List<ToolSpecification> specs) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are AgentOS, an AI agent with access to the following tools:\n\n");
        if (specs.isEmpty()) {
            sb.append("(No tools available)\n");
        } else {
            for (ToolSpecification spec : specs) {
                sb.append("- ").append(spec.name());
                if (spec.description() != null && !spec.description().isBlank()) {
                    sb.append(": ").append(spec.description());
                }
                sb.append("\n");
            }
        }
        sb.append("\n")
                .append("DATABASE SCHEMA:\n")
                .append(databaseSchema).append("\n")
                .append("IMPORTANT RULES:\n")
                .append("1. Always use the EXACT table and column names from DATABASE SCHEMA above.\n")
                .append("2. When you call database_query, it returns a JSON result. Use the EXACT data from that result — do not invent or change values.\n")
                .append("3. To chain tools: call database_query first, wait for the result, then call data_visualization with the actual data from the query result.\n")
                .append("4. The 'data' parameter of data_visualization must be the RAW JSON array of rows from database_query or query_uploaded_data — do not summarize, change, or fabricate data.\n")
                .append("5. query_uploaded_data returns data in the same JSON format as database_query, and its output can also be passed to data_visualization.\n")
                .append("6. Always output valid JSON: use double quotes (\") not single quotes (') for JSON strings.\n")
                .append("7. When you have enough information, respond with your final answer.\n");
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
        String desc = switch (tool.getName()) {
            case "database_query" ->
                "Execute a SQL SELECT query on the SQLite database. Schema:\n" + databaseSchema
                    + "Returns JSON rows.";
            case "data_visualization" ->
                "Generate an ECharts chart from REAL data. The 'data' parameter must be the ACTUAL JSON array returned by database_query — do not fabricate or modify values. Use double quotes not single quotes.";
            case "file_export" ->
                "Export data to a CSV file on the local filesystem. DESTRUCTIVE — requires human approval.";
            case "bash_execution" ->
                "Execute an arbitrary bash command. DESTRUCTIVE — requires human approval.";
            case "local_search" ->
                "Search local files and directories for content.";
            case "query_uploaded_data" ->
                "Query data from an uploaded file (CSV, XLSX, or PDF). Returns JSON rows in the same format as database_query. Output can be passed to data_visualization.";
            default ->
                tool.getName() + " tool.";
        };
        if (!tool.isConcurrencySafe()) {
            desc += " Not concurrency-safe — executes in isolation.";
        }
        if (tool.isDestructive()) {
            desc += " DESTRUCTIVE — may modify system state.";
        }
        return desc;
    }
}
