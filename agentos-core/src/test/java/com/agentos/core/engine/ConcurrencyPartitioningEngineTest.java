package com.agentos.core.engine;

import com.agentos.core.repository.ToolApprovalRepository;
import com.agentos.core.security.HumanApprovalInterceptor;
import com.agentos.core.security.PendingApprovalException;
import com.agentos.core.security.ToolInterceptor;
import com.agentos.core.security.ValidationInterceptor;
import com.agentos.core.session.ApprovalService;
import com.agentos.core.tool.ToolDefinition;
import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConcurrencyPartitioningEngine.
 * Uses mocked repositories for ApprovalService to avoid DB dependencies.
 */
class ConcurrencyPartitioningEngineTest {

    private ToolDefinition safeTool;
    private ToolDefinition destructiveTool;
    private ToolDefinition unsafeTool;
    private ConcurrencyPartitioningEngine engine;

    private static final List<ToolInterceptor> NO_INTERCEPTORS = List.of();

    @BeforeEach
    void setUp() {
        safeTool = mock(ToolDefinition.class);
        when(safeTool.getName()).thenReturn("safe_tool");
        when(safeTool.isConcurrencySafe()).thenReturn(true);
        when(safeTool.isDestructive()).thenReturn(false);

        destructiveTool = mock(ToolDefinition.class);
        when(destructiveTool.getName()).thenReturn("destructive_tool");
        when(destructiveTool.isConcurrencySafe()).thenReturn(false);
        when(destructiveTool.isDestructive()).thenReturn(true);

        unsafeTool = mock(ToolDefinition.class);
        when(unsafeTool.getName()).thenReturn("unsafe_tool");
        when(unsafeTool.isConcurrencySafe()).thenReturn(false);
        when(unsafeTool.isDestructive()).thenReturn(false);

        engine = new ConcurrencyPartitioningEngine(
                List.of(safeTool, destructiveTool, unsafeTool),
                Executors.newSingleThreadExecutor(),
                NO_INTERCEPTORS
        );
    }

    // Convenience factory for requests without tool call IDs
    private static ToolExecutionRequest req(String name) {
        return new ToolExecutionRequest(name, Map.of(), null);
    }

    // --- Partition boundary tests via package-private partition() ---

    @Test
    void allSafeTools_ProducesSingleBatch() {
        List<ToolExecutionRequest> requests = List.of(req("safe_tool"), req("safe_tool"), req("safe_tool"));
        List<List<ToolExecutionRequest>> batches = engine.partition(requests);

        assertEquals(1, batches.size(), "All safe tools should form one batch");
        assertEquals(3, batches.getFirst().size());
    }

    @Test
    void allDestructiveTools_ProducesOneBatchPerTool() {
        List<ToolExecutionRequest> requests = List.of(
                req("destructive_tool"), req("destructive_tool"), req("destructive_tool"));
        List<List<ToolExecutionRequest>> batches = engine.partition(requests);

        assertEquals(3, batches.size(), "Each destructive tool should be its own batch");
        batches.forEach(batch -> assertEquals(1, batch.size()));
    }

    @Test
    void allUnsafeTools_ProducesOneBatchPerTool() {
        List<ToolExecutionRequest> requests = List.of(req("unsafe_tool"), req("unsafe_tool"));
        List<List<ToolExecutionRequest>> batches = engine.partition(requests);

        assertEquals(2, batches.size(), "Each unsafe (non-concurrency-safe) tool should be its own batch");
    }

    @Test
    void mixedSequence_SafeSafeDestructiveSafe_ProducesThreeBatches() {
        List<ToolExecutionRequest> requests = List.of(
                req("safe_tool"), req("safe_tool"),
                req("destructive_tool"), req("safe_tool"));
        List<List<ToolExecutionRequest>> batches = engine.partition(requests);

        assertEquals(3, batches.size());
        assertEquals(2, batches.get(0).size(), "First batch: 2 safe tools");
        assertEquals(1, batches.get(1).size(), "Second batch: 1 destructive tool (isolated)");
        assertEquals(1, batches.get(2).size(), "Third batch: 1 safe tool (chain broken by destructive)");
    }

    @Test
    void unknownTool_IsIsolated() {
        List<ToolExecutionRequest> requests = List.of(req("unknown_tool"));
        List<List<ToolExecutionRequest>> batches = engine.partition(requests);

        assertEquals(1, batches.size());
        assertEquals(1, batches.getFirst().size(), "Unknown tool should be isolated");
    }

    @Test
    void emptyRequests_ProducesNoBatches() {
        assertTrue(engine.partition(List.of()).isEmpty());
    }

    @Test
    void singleSafeTool_ProducesOneBatch() {
        List<List<ToolExecutionRequest>> batches = engine.partition(List.of(req("safe_tool")));
        assertEquals(1, batches.size());
        assertEquals(1, batches.getFirst().size());
    }

    @Test
    void safeAfterDestructive_BreaksContiguousChain() {
        List<ToolExecutionRequest> requests = List.of(
                req("safe_tool"), req("destructive_tool"), req("safe_tool"), req("safe_tool"));
        List<List<ToolExecutionRequest>> batches = engine.partition(requests);

        assertEquals(3, batches.size());
        assertEquals(1, batches.get(0).size(), "First batch: single safe tool");
        assertEquals(1, batches.get(1).size(), "Second batch: isolated destructive tool");
        assertEquals(2, batches.get(2).size(), "Third batch: two safe tools grouped together");
    }

    // --- executePartitioned integration tests ---

    @Test
    void executePartitioned_ReturnsSameNumberOfResults() {
        List<ToolExecutionResult> results = engine.executePartitioned(List.of(
                req("safe_tool"), req("destructive_tool"), req("safe_tool")));

        assertEquals(3, results.size());
    }

    @Test
    void executePartitioned_SafeToolReturnsSuccess() {
        List<ToolExecutionResult> results = engine.executePartitioned(List.of(
                new ToolExecutionRequest("safe_tool", Map.of("query", "test"), null)));

        assertEquals(1, results.size());
        assertTrue(results.getFirst().success());
        assertEquals("safe_tool", results.getFirst().toolName());
    }

    @Test
    void executePartitioned_UnknownToolReturnsFailure() {
        List<ToolExecutionResult> results = engine.executePartitioned(List.of(req("nonexistent")));

        assertEquals(1, results.size());
        assertFalse(results.getFirst().success());
        assertEquals("nonexistent", results.getFirst().toolName());
        assertEquals("Unknown tool", results.getFirst().output());
    }

    // --- toolCallId propagation ---

    @Test
    void executePartitioned_PropagatesToolCallId() {
        List<ToolExecutionResult> results = engine.executePartitioned(List.of(
                new ToolExecutionRequest("safe_tool", Map.of(), "call_abc123")));

        assertEquals("call_abc123", results.getFirst().toolCallId());
    }

    // --- Interceptor chain tests ---

    @Test
    void validationInterceptor_BlocksNullToolName() {
        ToolInterceptor validator = new ValidationInterceptor();
        assertFalse(validator.preHandle(new ToolExecutionRequest(null, Map.of(), null)));
    }

    @Test
    void validationInterceptor_BlocksBlankToolName() {
        ToolInterceptor validator = new ValidationInterceptor();
        assertFalse(validator.preHandle(new ToolExecutionRequest("", Map.of(), null)));
    }

    @Test
    void validationInterceptor_BlocksNullArgs() {
        ToolInterceptor validator = new ValidationInterceptor();
        assertFalse(validator.preHandle(new ToolExecutionRequest("test", null, null)));
    }

    @Test
    void validationInterceptor_PassesValidRequest() {
        ToolInterceptor validator = new ValidationInterceptor();
        assertTrue(validator.preHandle(new ToolExecutionRequest("safe_tool", Map.of("k", "v"), null)));
    }

    @Test
    void humanApprovalInterceptor_ThrowsForDestructiveTool() {
        ToolDefinition destructive = mock(ToolDefinition.class);
        when(destructive.getName()).thenReturn("rm_tool");
        when(destructive.isDestructive()).thenReturn(true);

        ToolInterceptor approval = new HumanApprovalInterceptor(List.of(destructive),
                new ApprovalService(mock(ToolApprovalRepository.class)));

        assertThrows(PendingApprovalException.class,
                () -> approval.preHandle(new ToolExecutionRequest("rm_tool", Map.of(), null)));
    }

    @Test
    void humanApprovalInterceptor_PassesNonDestructiveTool() {
        ToolDefinition safe = mock(ToolDefinition.class);
        when(safe.getName()).thenReturn("search_tool");
        when(safe.isDestructive()).thenReturn(false);

        ToolInterceptor approval = new HumanApprovalInterceptor(List.of(safe),
                new ApprovalService(mock(ToolApprovalRepository.class)));

        assertDoesNotThrow(
                () -> approval.preHandle(new ToolExecutionRequest("search_tool", Map.of(), null)));
    }

    @Test
    void engineWithValidationInterceptor_BlocksInvalidRequest() {
        var engineWithValidation = new ConcurrencyPartitioningEngine(
                List.of(safeTool), Executors.newSingleThreadExecutor(),
                List.of(new ValidationInterceptor()));

        List<ToolExecutionResult> results = engineWithValidation.executePartitioned(List.of(
                new ToolExecutionRequest("safe_tool", null, "call_1")));

        assertEquals(1, results.size());
        assertFalse(results.getFirst().success());
        assertTrue(results.getFirst().output().contains("Blocked by ValidationInterceptor"));
    }

    @Test
    void engineWithHumanApprovalInterceptor_BlocksDestructiveTool() {
        var engineWithApproval = new ConcurrencyPartitioningEngine(
                List.of(destructiveTool), Executors.newSingleThreadExecutor(),
                List.of(new HumanApprovalInterceptor(List.of(destructiveTool),
                        new ApprovalService(mock(ToolApprovalRepository.class)))));

        List<ToolExecutionResult> results = engineWithApproval.executePartitioned(List.of(
                new ToolExecutionRequest("destructive_tool", Map.of(), "call_2")));

        assertEquals(1, results.size());
        assertFalse(results.getFirst().success());
        assertTrue(results.getFirst().output().contains("Requires Human Approval"));
    }
}
