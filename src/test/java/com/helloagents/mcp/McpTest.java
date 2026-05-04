package com.helloagents.mcp;

import com.helloagents.tools.CalculatorTool;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the full MCP round-trip:
 * <ol>
 *   <li>Start our own {@link AgentMcpServer} (with {@link CalculatorTool}) as a subprocess.</li>
 *   <li>Connect to it via {@link McpClientConnector}.</li>
 *   <li>List tools and call the calculator via the MCP protocol.</li>
 * </ol>
 *
 * Also covers {@link McpToolAdapter} schema conversion as unit tests.
 */
class McpTest {

    // ── adapter unit tests ────────────────────────────────────────────────────

    @Test
    void buildJsonSchema_requiredParam() {
        ToolParameter tp = ToolParameter.of(
            ToolParameter.Param.required("expression", "Math expression", "string")
        );
        McpSchema.JsonSchema schema = McpToolAdapter.buildJsonSchema(tp);

        assertEquals("object", schema.type());
        assertTrue(schema.properties().containsKey("expression"));
        assertEquals(List.of("expression"), schema.required());
    }

    @Test
    void buildJsonSchema_optionalParam_noRequired() {
        ToolParameter tp = ToolParameter.of(
            ToolParameter.Param.optional("precision", "Decimal places", "number")
        );
        McpSchema.JsonSchema schema = McpToolAdapter.buildJsonSchema(tp);

        assertTrue(schema.properties().containsKey("precision"));
        assertNull(schema.required());
    }

    @Test
    void buildJsonSchema_empty() {
        McpSchema.JsonSchema schema = McpToolAdapter.buildJsonSchema(ToolParameter.empty());

        assertEquals("object", schema.type());
        assertNull(schema.properties());
    }

    @Test
    void toMcpTool_mapsNameAndDescription() {
        McpServerFeatures.SyncToolSpecification spec = McpToolAdapter.toMcpTool(new CalculatorTool());

        assertEquals("calculate", spec.tool().name());
        assertFalse(spec.tool().description().isBlank());
        assertNotNull(spec.tool().inputSchema());
    }

    // ── integration: self-hosted server ──────────────────────────────────────

    @Test
    void selfHosted_listToolsAndCallCalculate() throws Exception {
        try (McpClientConnector mcp = McpClientConnector.stdio(
                javaExe(), "-cp", System.getProperty("java.class.path"),
                McpServerMain.class.getName())) {
            List<Tool> tools = mcp.listTools();

            assertFalse(tools.isEmpty(), "server should expose at least one tool");
            assertTrue(tools.stream().anyMatch(t -> "calculate".equals(t.name())),
                "calculate tool must be present");

            Tool calc = tools.stream()
                .filter(t -> "calculate".equals(t.name()))
                .findFirst().orElseThrow();

            assertEquals("42", calc.execute(Map.of("expression", "6 * 7")));
            assertEquals("10", calc.execute(Map.of("expression", "3 + 7")));
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String javaExe() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java")
                   .toString();
    }
}