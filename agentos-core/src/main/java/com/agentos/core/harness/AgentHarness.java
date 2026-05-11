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
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core agent harness — an async streaming loop that drives the agent:
 * <pre>
 *   LLM call → parse tool calls → execute via ConcurrencyPartitioningEngine →
 *   append results to state → loop until final text
 * </pre>
 */
@Component
public class AgentHarness {

    private static final Logger log = LoggerFactory.getLogger(AgentHarness.class);

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
     * Runs the agent loop for the given user prompt, streaming events as they
     * happen. The caller determines terminal behaviour (subscribe, block, etc.).
     */
    public Flux<AgentEvent> run(AgentState initialState, String userPrompt) {
        return Flux.create(emitter -> {
            AgentState state = initialState.appendMessage(ChatMessage.user(userPrompt));

            loop:
            while (true) {
                // 1. Build messages and call LLM
                emitter.next(new AgentEvent.ThinkingEvent("Sending to LLM..."));
                List<dev.langchain4j.data.message.ChatMessage> messages =
                        promptOrchestrator.buildMessages(state, userPrompt);

                Response<AiMessage> response = chatModel.generate(
                        messages, promptOrchestrator.toolSpecifications());
                AiMessage aiMessage = response.content();

                // 2. Check for tool calls
                if (aiMessage.hasToolExecutionRequests()) {
                    var lc4jRequests = aiMessage.toolExecutionRequests();

                    emitter.next(new AgentEvent.ThinkingEvent(
                            "Executing " + lc4jRequests.size() + " tool(s)..."));

                    // Map LangChain4j requests → our domain requests, preserving tool call IDs
                    List<ToolExecutionRequest> toolRequests = new ArrayList<>();
                    for (var lc4jReq : lc4jRequests) {
                        Map<String, String> args = parseArgs(lc4jReq.arguments());
                        toolRequests.add(new ToolExecutionRequest(
                                lc4jReq.name(), args, lc4jReq.id()));
                    }

                    // Execute via the partitioning engine (virtual-thread aware + security chain)
                    List<ToolExecutionResult> results =
                            partitioningEngine.executePartitioned(toolRequests);

                    // Emit tool execution events
                    emitter.next(new AgentEvent.ToolExecutionEvent(results));

                    // Update state: append assistant message + tool results
                    String assistantText = aiMessage.text() != null ? aiMessage.text() : "";
                    state = state.appendMessage(ChatMessage.assistant(assistantText));
                    for (ToolExecutionResult result : results) {
                        state = state.appendMessage(
                                ChatMessage.tool(result.toolName(), result.output(), result.toolCallId()));
                    }
                } else {
                    // 3. Final text response — no more tool calls
                    String text = aiMessage.text() != null ? aiMessage.text() : "";
                    state = state.appendMessage(ChatMessage.assistant(text));
                    emitter.next(new AgentEvent.FinalResponseEvent(text));
                    break loop;
                }
            }

            emitter.complete();
        });
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments JSON: {}", json, e);
            return Map.of();
        }
    }
}
