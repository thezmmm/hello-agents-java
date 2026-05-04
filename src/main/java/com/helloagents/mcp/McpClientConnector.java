package com.helloagents.mcp;

import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Connects to an external MCP server and exposes its tools as {@link Tool} instances
 * that can be registered into any {@link ToolRegistry}.
 *
 * <p>Typical usage — subprocess server via STDIO:
 * <pre>
 *   try (McpClientConnector mcp = McpClientConnector.stdio("npx", "-y", "@modelcontextprotocol/server-filesystem", "/tmp")) {
 *       mcp.registerAll(registry);
 *   }
 * </pre>
 *
 * <p>The connector is {@link AutoCloseable}: closing it shuts down the underlying
 * {@link McpSyncClient} and terminates the server subprocess (if any).
 */
public class McpClientConnector implements AutoCloseable {

    private final McpSyncClient client;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private McpClientConnector(McpClientTransport transport, String clientName, String clientVersion) {
        this.client = McpClient.sync(transport)
            .clientInfo(new McpSchema.Implementation(clientName, clientVersion))
            .requestTimeout(Duration.ofSeconds(30))
            .build();
        this.client.initialize();
    }

    /**
     * Connects to a subprocess MCP server launched with the given command and arguments.
     *
     * @param command  executable to run (e.g. {@code "npx"}, {@code "python"}, {@code "java"})
     * @param args     command-line arguments passed to the subprocess
     */
    public static McpClientConnector stdio(String command, String... args) {
        return stdio(command, Map.of(), args);
    }

    /**
     * Connects to a subprocess MCP server with explicit environment variables.
     *
     * @param command  executable to run (e.g. {@code "npx"}, {@code "python"})
     * @param env      extra environment variables for the subprocess
     * @param args     command-line arguments
     */
    public static McpClientConnector stdio(String command, Map<String, String> env, String... args) {
        ServerParameters params = buildParams(command, env, args);
        StdioClientTransport transport = new StdioClientTransport(params);
        return new McpClientConnector(transport, "hello-agents", "1.0.0");
    }

    /**
     * On Windows, {@link ProcessBuilder} cannot execute {@code .cmd}/{@code .bat} scripts
     * directly — they require {@code cmd.exe} as the interpreter. Native executables
     * ({@code .exe}, or absolute paths) can be launched by ProcessBuilder without wrapping.
     *
     * <p>Wraps in {@code cmd /c} only when on Windows AND the command is not a native executable,
     * avoiding the 8191-character command-line length limit that {@code cmd.exe} imposes.
     */
    private static ServerParameters buildParams(String command, Map<String, String> env, String[] args) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows && !isNativeExecutable(command)) {
            List<String> winArgs = new ArrayList<>();
            winArgs.add("/c");
            winArgs.add(command);
            winArgs.addAll(List.of(args));
            return ServerParameters.builder("cmd").args(winArgs).env(env).build();
        }
        return ServerParameters.builder(command).args(List.of(args)).env(env).build();
    }

    /** Returns true if the command is a native binary that ProcessBuilder can launch directly. */
    private static boolean isNativeExecutable(String command) {
        return command.endsWith(".exe") || command.contains("/") || command.contains("\\");
    }

    /**
     * Returns all tools advertised by the connected MCP server as {@link Tool} instances.
     * Follows MCP pagination: iterates all pages until {@code nextCursor} is null.
     */
    public List<Tool> listTools() {
        List<Tool> result = new ArrayList<>();
        String cursor = null;
        do {
            var page = cursor == null ? client.listTools() : client.listTools(cursor);
            page.tools().stream()
                .map(t -> (Tool) new McpRemoteTool(client, t, closed))
                .forEach(result::add);
            cursor = page.nextCursor();
        } while (cursor != null && !cursor.isBlank());
        return List.copyOf(result);
    }

    /**
     * Registers all tools from the connected server into the given {@link ToolRegistry}.
     */
    public void registerAll(ToolRegistry registry) {
        listTools().forEach(registry::register);
    }

    /**
     * Returns the underlying {@link McpSyncClient} for advanced use (e.g. resource/prompt access).
     */
    public McpSyncClient rawClient() {
        return client;
    }

    @Override
    public void close() {
        closed.set(true);
        client.closeGracefully();
    }
}