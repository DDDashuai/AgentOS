package com.agentos.core.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class BashExecutionTool implements ExecutableTool {

    private static final Logger log = LoggerFactory.getLogger(BashExecutionTool.class);

    static final long TIMEOUT_SECONDS = 60;
    static final int MAX_OUTPUT_CHARS = 100_000;

    @Override
    public String getName() {
        return "bash_execution";
    }

    @Override
    public boolean isConcurrencySafe() {
        return false;
    }

    @Override
    public boolean isDestructive() {
        return true;
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        Map<String, String> args = request.args();
        String command = args.get("command");
        if (command == null || command.isBlank()) {
            return new ToolExecutionResult(getName(), false,
                    "Missing required argument: 'command'", 0, request.toolCallId());
        }

        long start = System.currentTimeMillis();
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                long elapsed = System.currentTimeMillis() - start;
                log.warn("Bash command timed out after {}s: {}", TIMEOUT_SECONDS, command);
                return new ToolExecutionResult(getName(), false,
                        "Command timed out after " + TIMEOUT_SECONDS + " seconds", elapsed, request.toolCallId());
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (output.length() + line.length() + 1 > MAX_OUTPUT_CHARS) {
                        output.append("...\n[Output truncated at ").append(MAX_OUTPUT_CHARS).append(" characters]\n");
                        // Drain the rest without reading
                        while (reader.readLine() != null) {
                            // skip
                        }
                        break;
                    }
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.exitValue();
            long elapsed = System.currentTimeMillis() - start;

            // Trim trailing newline
            String result = output.toString().stripTrailing();

            if (exitCode == 0) {
                String msg = result.isBlank()
                        ? "Command completed successfully (exit code 0, no output)"
                        : result;
                return new ToolExecutionResult(getName(), true, msg, elapsed, request.toolCallId());
            } else {
                String msg = "Command failed (exit code " + exitCode + "):\n" + result;
                log.warn("Bash command failed: {}", msg);
                return new ToolExecutionResult(getName(), false, msg, elapsed, request.toolCallId());
            }

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Bash execution failed: {}", e.getMessage(), e);
            return new ToolExecutionResult(getName(), false,
                    "Execution error: " + e.getMessage(), elapsed, request.toolCallId());
        }
    }
}
