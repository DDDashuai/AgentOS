package com.agentos.core.llm;

import com.agentos.core.state.AgentState;
import com.agentos.core.state.ChatMessage;
import com.agentos.core.tool.ToolDefinition;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cache-aware prompt orchestrator.
 * <p>
 * The system prompt is built once at startup (static, containing tool schemas)
 * and never changes. Dynamic user history is appended separately. This strict
 * separation maximizes API prompt caching hit rates on the MLX server.
 */
@Component
public class PromptOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PromptOrchestrator.class);

    private final String systemPrompt;
    private final List<ToolSpecification> toolSpecifications;

    public PromptOrchestrator(List<ToolDefinition> tools) {
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

        // 3. Current user prompt
        messages.add(new UserMessage(userPrompt));

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
                .append("When you need to use a tool, respond with a valid tool call.\n")
                .append("When you have enough information, respond with your final answer.\n");
        return sb.toString();
    }

    private ToolSpecification toToolSpecification(ToolDefinition tool) {
        return ToolSpecification.builder()
                .name(tool.getName())
                .description(buildToolDescription(tool))
                .build();
    }

    private String buildToolDescription(ToolDefinition tool) {
        String desc = tool.getName() + " tool.";
        if (!tool.isConcurrencySafe()) {
            desc += " Not concurrency-safe — executes in isolation.";
        }
        if (tool.isDestructive()) {
            desc += " DESTRUCTIVE — may modify system state.";
        }
        return desc;
    }
}
