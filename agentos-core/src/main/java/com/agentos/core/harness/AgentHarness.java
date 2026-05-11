package com.agentos.core.harness;

import com.agentos.core.engine.ConcurrencyPartitioningEngine;
import com.agentos.core.llm.PromptOrchestrator;
import com.agentos.core.state.AgentEvent;
import com.agentos.core.state.AgentState;
import com.agentos.core.state.ChatMessage;
import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core agent harness — an async streaming loop that drives the agent:
 * <pre>
 *   LLM call → parse text-based [TOOL_CALL] → execute tools →
 *   append results → loop until no more tool calls → emit final
 * </pre>
 * <p>
 * Uses a text-based tool call format instead of native OpenAI function calling
 * because the MLX server's Qwen2-7B model does not support structured tool calls.
 * The format is: {@code [TOOL_CALL] {"name": "...", "arguments": {...}} [/TOOL_CALL]}
 */
@Component
public class AgentHarness {

    private static final Logger log = LoggerFactory.getLogger(AgentHarness.class);

    /** Safety limit: break agent loop after this many iterations to prevent runaway loops. */
    private static final int MAX_ITERATIONS = 10;

    /** Regex to extract [TOOL_CALL] blocks from LLM text output. */
    private static final Pattern TOOL_CALL_PATTERN =
            Pattern.compile("\\[TOOL_CALL\\]\\s*(\\{.*?\\})\\s*\\[/TOOL_CALL\\]", Pattern.DOTALL);

    private final ChatLanguageModel chatModel;
    private final ConcurrencyPartitioningEngine partitioningEngine;
    private final PromptOrchestrator promptOrchestrator;
    private final ObjectMapper objectMapper;

    public AgentHarness(
            ChatLanguageModel chatModel,
            ConcurrencyPartitioningEngine partitioningEngine,
            PromptOrchestrator promptOrchestrator,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.partitioningEngine = partitioningEngine;
        this.promptOrchestrator = promptOrchestrator;
        this.objectMapper = objectMapper;
    }

    /**
     * Build the initial message list for the first LLM call.
     * Creates system message + conversation history + current user prompt.
     */
    private List<dev.langchain4j.data.message.ChatMessage> buildInitialMessages(
            AgentState state, String userPrompt) {
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptOrchestrator.systemPrompt()));

        for (ChatMessage msg : state.history()) {
            addToMessages(messages, msg);
        }

        messages.add(new UserMessage(userPrompt));
        return messages;
    }

    /**
     * Append a tool turn to the message list — the assistant's tool call response + tool results.
     */
    private void appendToolTurn(
            List<dev.langchain4j.data.message.ChatMessage> messages,
            String toolCallText,
            String aiResponsePrefix,
            List<ToolExecutionResult> results) {
        // Add assistant message with the tool call it made
        String fullText = aiResponsePrefix != null && !aiResponsePrefix.isEmpty()
                ? aiResponsePrefix + "\n" + toolCallText
                : toolCallText;
        messages.add(new AiMessage(fullText));

        // Add tool results
        for (ToolExecutionResult result : results) {
            messages.add(new ToolExecutionResultMessage(
                    result.toolCallId(), result.toolName(), result.output()));
        }
    }

    private void addToMessages(
            List<dev.langchain4j.data.message.ChatMessage> messages, ChatMessage msg) {
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

    /**
     * Parse [TOOL_CALL] blocks from the LLM's text response.
     * Returns the list of parsed tool call requests, or empty list if none found.
     */
    private List<ParsedToolCall> parseToolCalls(String text) {
        List<ParsedToolCall> calls = new ArrayList<>();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            String json = matcher.group(1).trim();
            try {
                Map<String, Object> parsed = objectMapper.readValue(json,
                        new TypeReference<Map<String, Object>>() {});
                String name = (String) parsed.get("name");
                @SuppressWarnings("unchecked")
                Map<String, String> args = parsed.get("arguments") instanceof Map<?, ?> m
                        ? objectMapper.convertValue(m, new TypeReference<Map<String, String>>() {})
                        : Map.of();
                if (name != null && !name.isBlank()) {
                    calls.add(new ParsedToolCall(name, args));
                } else {
                    log.warn("Tool call missing 'name' field: {}", json);
                }
            } catch (Exception e) {
                log.warn("Failed to parse tool call JSON: {} — {}", json, e.getMessage());
            }
        }
        return calls;
    }

    /**
     * Runs the agent loop for the given user prompt, streaming events as they
     * happen.
     */
    public Flux<AgentEvent> run(AgentState initialState, String userPrompt) {
        return Flux.create(emitter -> {
            AgentState state = initialState;
            int iteration = 0;

            // Build the initial message list
            List<dev.langchain4j.data.message.ChatMessage> messages =
                    buildInitialMessages(state, userPrompt);

            loop:
            while (iteration++ < MAX_ITERATIONS) {
                // 1. Call LLM (no tool specifications — the prompt instructs the format)
                emitter.next(new AgentEvent.ThinkingEvent("Sending to LLM..."));
                Response<AiMessage> response = chatModel.generate(messages);
                AiMessage aiMessage = response.content();
                String text = aiMessage.text() != null ? aiMessage.text() : "";

                // 2. Parse text for [TOOL_CALL] blocks
                List<ParsedToolCall> parsedCalls = parseToolCalls(text);

                if (!parsedCalls.isEmpty()) {
                    emitter.next(new AgentEvent.ThinkingEvent(
                            "Executing " + parsedCalls.size() + " tool(s)..."));

                    // Strip tool call blocks for the assistant message history
                    String cleanText = text.replaceAll("\\s*\\[TOOL_CALL\\].*?\\[/TOOL_CALL\\]\\s*", " ").trim();

                    // Map parsed calls → domain tool execution requests
                    List<ToolExecutionRequest> toolRequests = new ArrayList<>();
                    for (ParsedToolCall call : parsedCalls) {
                        toolRequests.add(new ToolExecutionRequest(
                                call.name(), call.arguments(),
                                "call_" + System.nanoTime(), state.sessionId()));
                    }

                    // Execute via the partitioning engine
                    List<ToolExecutionResult> results =
                            partitioningEngine.executePartitioned(toolRequests);

                    // Emit tool execution events
                    emitter.next(new AgentEvent.ToolExecutionEvent(results));

                    // Append this tool turn to the message list
                    appendToolTurn(messages, text, cleanText, results);

                    // Update in-memory state
                    state = state.appendMessage(ChatMessage.assistant(cleanText));
                    for (ToolExecutionResult result : results) {
                        state = state.appendMessage(
                                ChatMessage.tool(result.toolName(), result.output(), result.toolCallId()));
                    }
                } else {
                    // 3. No tool calls — this is the final text response
                    state = state.appendMessage(ChatMessage.assistant(text));
                    emitter.next(new AgentEvent.FinalResponseEvent(text));
                    break loop;
                }
            }

            if (iteration >= MAX_ITERATIONS) {
                log.warn("Agent loop exceeded max iterations ({}) for session {}", MAX_ITERATIONS, state.sessionId());
                emitter.next(new AgentEvent.FinalResponseEvent(
                        "I've reached the maximum number of steps. Please try a simpler request or rephrase."));
            }

            emitter.complete();
        });
    }

    /** Internal record for a parsed [TOOL_CALL] block. */
    private record ParsedToolCall(String name, Map<String, String> arguments) {}
}
