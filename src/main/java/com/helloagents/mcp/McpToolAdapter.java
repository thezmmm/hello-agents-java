package com.helloagents.mcp;

import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a {@link Tool} into an MCP {@link McpServerFeatures.SyncToolSpecification}.
 *
 * <p>The conversion maps our {@link ToolParameter} model to {@link McpSchema.JsonSchema},
 * which is the format MCP clients expect in the {@code inputSchema} field.
 */
public final class McpToolAdapter {

    private McpToolAdapter() {}

    public static McpServerFeatures.SyncToolSpecification toMcpTool(Tool tool) {
        McpSchema.JsonSchema schema = buildJsonSchema(tool.parameters());
        McpSchema.Tool mcpTool = new McpSchema.Tool(tool.name(), tool.description(), schema);

        return new McpServerFeatures.SyncToolSpecification(
            mcpTool,
            (exchange, args) -> {
                Map<String, String> params = stringifyArgs(args);
                String result;
                try {
                    result = tool.execute(params);
                } catch (Exception e) {
                    return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error: " + e.getMessage())), true);
                }
                return new McpSchema.CallToolResult(
                    List.of(new McpSchema.TextContent(result)), false);
            }
        );
    }

    /**
     * Builds a {@link McpSchema.JsonSchema} from a {@link ToolParameter}.
     *
     * <pre>
     * { "type": "object",
     *   "properties": { "expression": { "type": "string", "description": "..." } },
     *   "required": ["expression"] }
     * </pre>
     */
    static McpSchema.JsonSchema buildJsonSchema(ToolParameter tp) {
        if (tp.isEmpty()) {
            return new McpSchema.JsonSchema("object", null, null, null);
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ToolParameter.Param p : tp.list()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", p.type());
            prop.put("description", p.description());
            properties.put(p.name(), prop);
            if (p.required()) required.add(p.name());
        }

        return new McpSchema.JsonSchema(
            "object",
            properties,
            required.isEmpty() ? null : required,
            null
        );
    }

    private static Map<String, String> stringifyArgs(Map<String, Object> args) {
        Map<String, String> result = new LinkedHashMap<>();
        if (args != null) {
            args.forEach((k, v) -> result.put(k, v != null ? v.toString() : ""));
        }
        return result;
    }
}