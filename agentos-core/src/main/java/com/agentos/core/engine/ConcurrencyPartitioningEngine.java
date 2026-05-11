package com.agentos.core.engine;

import com.agentos.core.security.PendingApprovalException;
import com.agentos.core.security.ToolInterceptor;
import com.agentos.core.tool.ExecutableTool;
import com.agentos.core.tool.ToolDefinition;
import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class ConcurrencyPartitioningEngine {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyPartitioningEngine.class);

    private final Map<String, ToolDefinition> toolRegistry;
    private final Executor executor;
    private final List<ToolInterceptor> interceptors;

    public ConcurrencyPartitioningEngine(
            List<ToolDefinition> tools,
            Executor virtualThreadExecutor,
            List<ToolInterceptor> interceptors) {
        this.toolRegistry = tools.stream()
                .collect(Collectors.toUnmodifiableMap(ToolDefinition::getName, t -> t));
        this.executor = virtualThreadExecutor;
        this.interceptors = List.copyOf(interceptors);
        log.info("Registered {} tools: {} with {} interceptor(s)",
                toolRegistry.size(), toolRegistry.keySet(), interceptors.size());
    }

    /**
     * Partitions the given requests and executes them.
     * <p>
     * Consecutive concurrency-safe, non-destructive tools are batched and executed
     * in
     * parallel via {@link CompletableFuture#allOf(CompletableFuture[])} on virtual
     * threads.
     * Any tool that is destructive or not concurrency-safe is executed in its own
     * isolated batch, sequentially.
     */
    public List<ToolExecutionResult> executePartitioned(List<ToolExecutionRequest> requests) {
        List<List<ToolExecutionRequest>> batches = partition(requests);
        List<ToolExecutionResult> results = new ArrayList<>(requests.size());

        for (List<ToolExecutionRequest> batch : batches) {
            if (batch.size() == 1 && shouldIsolate(batch.getFirst())) {
                ToolExecutionRequest req = batch.getFirst();
                results.add(executeSequential(req));
            } else {
                results.addAll(executeParallel(batch));
            }
        }

        return results;
    }

    /**
     * Partitions requests into batches based on concurrency safety and
     * destructiveness.
     */
    List<List<ToolExecutionRequest>> partition(List<ToolExecutionRequest> requests) {
        List<List<ToolExecutionRequest>> batches = new ArrayList<>();
        List<ToolExecutionRequest> currentBatch = new ArrayList<>();

        for (ToolExecutionRequest req : requests) {
            if (currentBatch.isEmpty()) {
                if (shouldIsolate(req)) {
                    batches.add(List.of(req));
                } else {
                    currentBatch.add(req);
                }
            } else if (shouldIsolate(req)) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
                batches.add(List.of(req));
            } else {
                currentBatch.add(req);
            }
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        log.debug("Partitioned {} requests into {} batches", requests.size(), batches.size());
        return batches;
    }

    private boolean shouldIsolate(ToolExecutionRequest req) {
        ToolDefinition def = resolveTool(req.toolName());
        return def == null || !def.isConcurrencySafe() || def.isDestructive();
    }

    private ToolDefinition resolveTool(String name) {
        ToolDefinition def = toolRegistry.get(name);
        if (def == null) {
            log.warn("No tool registered with name '{}'", name);
        }
        return def;
    }

    private ToolExecutionResult executeSequential(ToolExecutionRequest req) {
        log.info("Executing isolated (sequential): {}", req.toolName());
        return executeTool(req);
    }

    private List<ToolExecutionResult> executeParallel(List<ToolExecutionRequest> batch) {
        log.info("Executing batch in parallel: {} tools", batch.size());

        List<CompletableFuture<ToolExecutionResult>> futures = batch.stream()
                .map(req -> CompletableFuture.supplyAsync(() -> executeTool(req), executor))
                .toList();

        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            all.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Parallel execution interrupted", e);
        } catch (ExecutionException e) {
            log.error("One or more parallel tools failed", e.getCause());
        }

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private ToolExecutionResult executeTool(ToolExecutionRequest req) {
        // Run interceptor chain
        for (ToolInterceptor interceptor : interceptors) {
            try {
                if (!interceptor.preHandle(req)) {
                    log.warn("Tool '{}' blocked by {}", req.toolName(),
                            interceptor.getClass().getSimpleName());
                    return new ToolExecutionResult(req.toolName(), false,
                            "Blocked by " + interceptor.getClass().getSimpleName(), 0, req.toolCallId());
                }
            } catch (PendingApprovalException e) {
                log.error("Tool '{}' requires human approval: {}", req.toolName(), e.getMessage());
                return new ToolExecutionResult(req.toolName(), false,
                        "Requires Human Approval: " + e.getMessage(), 0, req.toolCallId());
            }
        }

        // Resolve tool definition
        ToolDefinition def = resolveTool(req.toolName());
        if (def == null) {
            return new ToolExecutionResult(req.toolName(), false, "Unknown tool", 0, req.toolCallId());
        }

        // Dispatch to ExecutableTool if available, otherwise simulate
        long start = System.currentTimeMillis();
        if (def instanceof ExecutableTool executable) {
            return executable.execute(req);
        }

        // Legacy simulation fallback for non-ExecutableTool definitions
        try {
            log.debug("Simulating tool: {} with args: {}", req.toolName(), req.args());
            Thread.sleep(50);
            long elapsed = System.currentTimeMillis() - start;
            return new ToolExecutionResult(req.toolName(), true,
                    "Executed " + req.toolName() + " with " + req.args().size() + " arg(s)",
                    elapsed, req.toolCallId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolExecutionResult(req.toolName(), false, "Interrupted",
                    System.currentTimeMillis() - start, req.toolCallId());
        }
    }
}
