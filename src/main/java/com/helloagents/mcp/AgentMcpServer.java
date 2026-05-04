package com.helloagents.mcp;

import com.helloagents.tools.Tool;
import com.helloagents.tools.Toolkit;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Builds and runs an MCP server over STDIO using the project's {@link Tool} abstractions.
 *
 * <p>Usage:
 * <pre>
 *   new AgentMcpServer("hello-agents", "1.0.0")
 *       .addTool(new CalculatorTool())
 *       .addToolkit(new MemoryToolkit(...))
 *       .runBlocking();
 * </pre>
 *
 * <p>The server communicates over stdin/stdout (STDIO transport), which is the standard
 * mechanism for MCP servers launched as subprocesses by clients such as Claude Desktop.
 */
public class AgentMcpServer {

    private final String name;
    private final String version;
    private final List<Tool> tools = new ArrayList<>();
    private final AtomicBoolean built = new AtomicBoolean(false);

    public AgentMcpServer(String name, String version) {
        this.name = name;
        this.version = version;
    }

    public AgentMcpServer addTool(Tool tool) {
        tools.add(tool);
        return this;
    }

    public AgentMcpServer addToolkit(Toolkit toolkit) {
        tools.addAll(toolkit.getTools());
        return this;
    }

    /**
     * Builds the {@link McpSyncServer} without blocking.
     * Useful when you need access to the server instance (e.g. dynamic tool registration).
     *
     * <p>May only be called once — each call creates a new {@link StdioServerTransportProvider}
     * that reads from {@code System.in}; calling it twice causes two providers to race on stdin.
     *
     * @throws IllegalStateException if called more than once
     */
    public McpSyncServer build() {
        if (!built.compareAndSet(false, true)) {
            throw new IllegalStateException(
                "build() may only be called once per AgentMcpServer instance");
        }
        List<McpServerFeatures.SyncToolSpecification> specs = tools.stream()
            .map(McpToolAdapter::toMcpTool)
            .toList();

        return McpServer.sync(new StdioServerTransportProvider())
            .serverInfo(name, version)
            .capabilities(McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build())
            .tools(specs)
            .build();
    }

    /**
     * Builds the server, registers a shutdown hook for graceful close,
     * and parks the main thread until the JVM exits (stdin EOF or SIGTERM).
     *
     * <p>The STDIO reader runs on a background reactor thread; the main thread
     * must stay alive or the JVM will exit prematurely.
     */
    public void runBlocking() throws InterruptedException {
        McpSyncServer server = build();
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
        Thread.currentThread().join();
    }
}