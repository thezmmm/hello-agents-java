package com.helloagents.mcp;

import com.helloagents.tools.CalculatorTool;

/**
 * Subprocess entry point used by {@link McpTest} to start an in-process MCP server.
 * Not a demo — launched via {@code java -cp <classpath> McpServerMain} by the test.
 */
public class McpServerMain {

    public static void main(String[] args) throws InterruptedException {
        new AgentMcpServer("hello-agents-test", "1.0.0")
            .addTool(new CalculatorTool())
            .runBlocking();
    }
}