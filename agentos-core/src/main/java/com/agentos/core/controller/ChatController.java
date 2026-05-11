package com.agentos.core.controller;

import com.agentos.core.harness.AgentHarness;
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

    /** Lightweight in-memory session state store. */
    private final Map<String, AgentState> sessions = new ConcurrentHashMap<>();

    public ChatController(AgentHarness agentHarness, ApprovalService approvalService, ObjectMapper objectMapper) {
        this.agentHarness = agentHarness;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /api/chat — sends a message to the agent and streams back SSE events.
     * <p>
     * Events: {@code data: {"type":"thinking","thought":"..."}\n\n}
     *         {@code data: {"type":"tool_execution","results":[...]}\n\n}
     *         {@code data: {"type":"hitl_required","toolName":"...","message":"..."}\n\n}
     *         {@code data: {"type":"final","response":"...","sessionId":"..."}\n\n}
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) {
            return Flux.just(sse("error", Map.of("message", "message is required")));
        }

        String sessionId = body.getOrDefault("sessionId", UUID.randomUUID().toString());
        AgentState state = sessions.computeIfAbsent(sessionId,
                id -> new AgentState(id, List.of(), Map.of()));

        log.info("[{}] Chat request: {}", sessionId, message);

        return agentHarness.run(state, message)
                .concatMap(event -> Flux.fromIterable(toSseEvents(event)))
                .doFinally(sig -> log.debug("[{}] Stream complete: {}", sessionId, sig));
    }

    /**
     * POST /api/chat/approve — pre-approves a destructive tool for a session.
     * <p>
     * The client should then send a follow-up chat message to retry the
     * approved tool.
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

    private List<String> toSseEvents(AgentEvent event) {
        return switch (event) {
            case AgentEvent.ThinkingEvent t ->
                    List.of(sse("thinking", Map.of("thought", t.thought())));

            case AgentEvent.ToolExecutionEvent e -> {
                // Check if any tool result requires human approval
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
                String sessionId = findSessionId();
                List<String> events = new java.util.ArrayList<>();
                events.add(sse("final", Map.of(
                        "response", f.response(),
                        "sessionId", sessionId != null ? sessionId : "")));
                events.add(sse("[DONE]", Map.of()));
                yield events;
            }
        };
    }

    /**
     * Reconstructs a sessionId from the current AgentState if possible.
     * Since AgentEvent does not carry session context, we do a best-effort
     * lookup from the last-used session key.
     */
    private String findSessionId() {
        // Best-effort: use the last entry from sessions
        return sessions.isEmpty() ? null : sessions.keySet().stream().reduce((a, b) -> b).orElse(null);
    }

    private String sse(String type, Object data) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", type);
            // If data is a map, merge it
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
            // The tool_execution results field can be large — handle it via POJO
            return "data: " + objectMapper.writeValueAsString(node) + "\n\n";
        } catch (Exception e) {
            log.error("Failed to serialize SSE event", e);
            return "data: {\"type\":\"error\",\"message\":\"serialization failed\"}\n\n";
        }
    }
}
