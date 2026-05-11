package com.agentos.core.tools.impl;

import com.agentos.core.tool.BashExecutionTool;
import com.agentos.core.tool.ToolExecutionRequest;
import com.agentos.core.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BashExecutionToolTest {

    private final BashExecutionTool tool = new BashExecutionTool();

    @Test
    void executesSimpleCommand() {
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("bash_execution", Map.of("command", "echo hello world"), null));

        assertTrue(result.success(), "Command should succeed: " + result.output());
        assertEquals("hello world", result.output());
    }

    @Test
    void capturesMultiLineOutput() {
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("bash_execution",
                        Map.of("command", "echo line1 && echo line2 && echo line3"), null));

        assertTrue(result.success());
        assertEquals("line1\nline2\nline3", result.output());
    }

    @Test
    void returnsNonZeroExitCode() {
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("bash_execution", Map.of("command", "exit 42"), null));

        assertFalse(result.success());
        assertTrue(result.output().contains("exit code 42"));
    }

    @Test
    void reportsCommandNotFound() {
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("bash_execution", Map.of("command", "nonexistent_command_xyz"), null));

        assertFalse(result.success());
        assertTrue(result.output().contains("exit code") || result.output().contains("not found"));
    }

    @Test
    void missingCommandArg() {
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("bash_execution", Map.of(), null));

        assertFalse(result.success());
        assertTrue(result.output().contains("Missing"));
    }

    @Test
    void blankCommandArg() {
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("bash_execution", Map.of("command", "   "), null));

        assertFalse(result.success());
        assertTrue(result.output().contains("Missing"));
    }

    @Test
    void inheritsWorkingDirectory() {
        ToolExecutionResult result = tool.execute(
                new ToolExecutionRequest("bash_execution", Map.of("command", "pwd"), null));

        assertTrue(result.success(), result.output());
        // Should be the project directory or home — just check it returns a real path
        assertTrue(result.output().startsWith("/"));
    }

    @Test
    void destructiveFlag() {
        assertTrue(tool.isDestructive());
        assertFalse(tool.isConcurrencySafe());
    }
}
