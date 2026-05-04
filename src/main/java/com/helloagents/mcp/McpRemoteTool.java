package com.helloagents.mcp;

import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * A {@link Tool} that delegates execution to a remote MCP server via {@link McpSyncClient}.
 *
 * <p>Instances are created by {@link McpClientConnector#listTools()} — one per tool
 * advertised by the connected server. The client is shared across all tools from the
 * same server; closing the connector invalidates all its tools.
 */
public class McpRemoteTool implements Tool {

    private final McpSyncClient client;
    private final McpSchema.Tool mcpTool;
    private final AtomicBoolean connectorClosed;

    McpRemoteTool(McpSyncClient client, McpSchema.Tool mcpTool, AtomicBoolean connectorClosed) {
        this.client = client;
        this.mcpTool = mcpTool;
        this.connectorClosed = connectorClosed;
    }

    @Override
    public String name() {
        return mcpTool.name();
    }

    @Override
    public String description() {
        return mcpTool.description();
    }

    @Override
    public ToolParameter parameters() {
        return parseJsonSchema(mcpTool.inputSchema());
    }

    @Override
    public String execute(Map<String, String> params) {
        if (connectorClosed.get()) {
            throw new IllegalStateException(
                "MCP connector has been closed; tool '" + mcpTool.name() + "' is no longer available");
        }
        Map<String, Object> args = coerceArgs(params, mcpTool.inputSchema());
        McpSchema.CallToolResult result = client.callTool(
            new McpSchema.CallToolRequest(mcpTool.name(), args));
        if (Boolean.TRUE.equals(result.isError())) {
            throw new RuntimeException("MCP tool error: " + extractText(result));
        }
        return extractText(result);
    }

    /**
     * Converts string param values to the JSON types declared in the schema.
     *
     * <p>The agent always produces {@code Map<String,String>} from LLM function-call
     * arguments, but MCP servers may reject string "1234" where they expect number 1234.
     * This method coerces values using the schema's {@code type} field before serialization.
     */
    private static Map<String, Object> coerceArgs(Map<String, String> params, McpSchema.JsonSchema schema) {
        Map<String, Object> properties = schema != null ? schema.properties() : null;
        Map<String, Object> args = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String type = "string";
            if (properties != null && properties.get(e.getKey()) instanceof Map<?, ?> prop
                    && prop.get("type") instanceof String t) {
                type = t;
            }
            args.put(e.getKey(), coerce(e.getValue(), type));
        }
        return args;
    }

    private static Object coerce(String value, String jsonType) {
        return switch (jsonType) {
            case "integer" -> {
                try { yield Long.parseLong(value.trim()); }
                catch (NumberFormatException e) { yield value; }
            }
            case "number" -> {
                try { yield Double.parseDouble(value.trim()); }
                catch (NumberFormatException e) { yield value; }
            }
            case "boolean" -> Boolean.parseBoolean(value.trim());
            default -> value;
        };
    }

    /**
     * Converts the MCP JSON Schema back to our {@link ToolParameter} model.
     * Property values are Jackson-deserialized {@code LinkedHashMap<String, Object>}.
     */
    private static ToolParameter parseJsonSchema(McpSchema.JsonSchema schema) {
        if (schema == null || schema.properties() == null) return ToolParameter.empty();

        List<String> required = schema.required() != null ? schema.required() : List.of();
        List<ToolParameter.Param> params = new ArrayList<>();

        for (Map.Entry<String, Object> entry : schema.properties().entrySet()) {
            String paramName = entry.getKey();
            String type = "string";
            String description = "";

            if (entry.getValue() instanceof Map<?, ?> prop) {
                if (prop.get("type") instanceof String s) type = s;
                if (prop.get("description") instanceof String s) description = s;
            }

            boolean isRequired = required.contains(paramName);
            params.add(isRequired
                ? ToolParameter.Param.required(paramName, description, type)
                : ToolParameter.Param.optional(paramName, description, type));
        }

        return ToolParameter.of(params.toArray(new ToolParameter.Param[0]));
    }

    private static String extractText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) return "";
        return result.content().stream()
            .map(c -> c instanceof McpSchema.TextContent tc
                ? tc.text()
                : "[non-text content: " + c.getClass().getSimpleName() + "]")
            .collect(Collectors.joining("\n"));
    }
}