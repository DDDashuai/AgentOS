package com.agentos.core.controller;

import com.agentos.core.entity.ChatMessageEntity;
import com.agentos.core.entity.ChatSessionEntity;
import com.agentos.core.harness.AgentHarness;
import com.agentos.core.repository.ChatMessageRepository;
import com.agentos.core.repository.ChatSessionRepository;
import com.agentos.core.security.PendingApprovalException;
import com.agentos.core.session.ApprovalService;
import com.agentos.core.state.AgentEvent;
import com.agentos.core.state.AgentState;
import com.agentos.core.state.ChatMessage;
import com.agentos.core.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final AgentHarness agentHarness;
    private final ApprovalService approvalService;
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    /** Lightweight in-memory session state store (agent execution context). */
    private final Map<String, AgentState> sessions = new ConcurrentHashMap<>();

    public ChatController(AgentHarness agentHarness, ApprovalService approvalService,
                          ObjectMapper objectMapper,
                          ChatSessionRepository sessionRepository,
                          ChatMessageRepository messageRepository) {
        this.agentHarness = agentHarness;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * POST /api/chat — sends a message to the agent and streams back SSE events.
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Flux.just(sse("error", Map.of("message", "message is required")));
        }

        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());

        // Ensure session exists in DB
        UUID sessionUuid = toSessionUuid(sessionId);
        sessionRepository.findById(sessionUuid).orElseGet(() -> {
            var s = new ChatSessionEntity(sessionUuid, truncateTitle(message));
            return sessionRepository.save(s);
        });

        // Get or create agent execution state (loads messages from DB — the
        // current user message is added by AgentHarness before the LLM call)
        AgentState state = sessions.computeIfAbsent(sessionId,
                id -> buildAgentState(sessionId));

        log.info("[{}] Chat request: {}", sessionId, message);

        final String finalSessionId = sessionId;
        final UUID finalSessionUuid = sessionUuid;
        return agentHarness.run(state, message)
                .concatMap(event -> Flux.fromIterable(toSseEvents(event, finalSessionId)))
                .doFinally(sig -> {
                    // Persist user message after the stream completes
                    messageRepository.save(new ChatMessageEntity(
                            UUID.randomUUID(), finalSessionUuid, "user", message, null));
                    log.debug("[{}] Stream complete: {}", finalSessionId, sig);
                });
    }

    /**
     * POST /api/chat/approve — pre-approves a destructive tool for a session.
     */
    @PostMapping("/approve")
    public Map<String, Object> approve(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        String toolName = body.get("toolName");

        if (sessionId == null || toolName == null) {
            return Map.of("success", false, "message", "sessionId and toolName are required");
        }

        approvalService.approve(sessionId, toolName);
        log.info("[{}] Approved tool: {}", sessionId, toolName);

        return Map.of("success", true, "sessionId", sessionId, "toolName", toolName);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AgentState buildAgentState(String sessionId) {
        try {
            UUID sessionUuid = toSessionUuid(sessionId);
            var entities = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionUuid);
            var history = entities.stream()
                    .map(m -> new ChatMessage(m.getRole(), m.getContent(), m.getToolCallId()))
                    .toList();
            log.info("[{}] Restored {} messages from database", sessionId, history.size());
            return new AgentState(sessionId, history, Map.of());
        } catch (IllegalArgumentException e) {
            return new AgentState(sessionId, List.of(), Map.of());
        }
    }

    private List<String> toSseEvents(AgentEvent event, String sessionId) {
        return switch (event) {
            case AgentEvent.ThinkingEvent t ->
                    List.of(sse("thinking", Map.of("thought", t.thought())));

            case AgentEvent.ToolExecutionEvent e -> {
                boolean hitlRequired = false;
                String hitlTool = null;
                for (ToolExecutionResult r : e.results()) {
                    if (!r.success() && r.output().contains("Requires Human Approval")) {
                        hitlRequired = true;
                        hitlTool = r.toolName();
                        break;
                    }
                }
                if (hitlRequired) {
                    yield List.of(
                            sse("tool_execution", Map.of("results", e.results())),
                            sse("hitl_required", Map.of(
                                    "toolName", hitlTool,
                                    "message", "Tool '" + hitlTool + "' requires human approval")));
                }
                yield List.of(sse("tool_execution", Map.of("results", e.results())));
            }

            case AgentEvent.FinalResponseEvent f -> {
                // Persist assistant response to DB
                persistAssistantMessage(sessionId, f.response());

                List<String> events = new java.util.ArrayList<>();
                events.add(sse("final", Map.of(
                        "response", f.response(),
                        "sessionId", sessionId)));
                events.add(sse("[DONE]", Map.of()));
                yield events;
            }
        };
    }

    private void persistAssistantMessage(String sessionId, String content) {
        try {
            UUID sessionUuid = toSessionUuid(sessionId);
            messageRepository.save(new ChatMessageEntity(
                    UUID.randomUUID(), sessionUuid, "assistant", content, null));
        } catch (IllegalArgumentException e) {
            log.warn("Failed to persist assistant message: {}", e.getMessage());
        }
    }

    private String sse(String type, Object data) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", type);
            if (data instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (v instanceof String s) node.put((String) k, s);
                    else if (v instanceof Boolean b) node.put((String) k, b);
                    else if (v instanceof Number n) node.put((String) k, n.doubleValue());
                    else node.putPOJO((String) k, v);
                });
            } else {
                node.putPOJO("payload", data);
            }
            // Spring WebFlux adds "data:" prefix and "\n\n" suffix automatically
            // when produces=TEXT_EVENT_STREAM_VALUE. Add a leading space so the
            // output is "data: {...}" (SSE spec allows both forms; space matches
            // what the frontend EventSource parser expects).
            return " " + objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.error("Failed to serialize SSE event", e);
            return "{\"type\":\"error\",\"message\":\"serialization failed\"}";
        }
    }

    private static UUID toSessionUuid(String sessionId) {
        try {
            return UUID.fromString(sessionId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(sessionId.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String truncateTitle(String message) {
        return message.length() > 100 ? message.substring(0, 100) + "..." : message;
    }
}
