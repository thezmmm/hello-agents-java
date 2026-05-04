package com.helloagents.demo.mcp;

import com.helloagents.agents.ReActAgent;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.mcp.McpClientConnector;
import com.helloagents.tools.Tool;

import java.util.List;
import java.util.Map;

/**
 * MCP Client 使用展示 — 三个场景：
 * <ol>
 *   <li>列出远程 MCP server 的全部工具</li>
 *   <li>直接调用远程工具（无需 LLM）</li>
 *   <li>将远程工具挂载到 ReActAgent，由 LLM 驱动调用</li>
 * </ol>
 *
 * <p>前置条件：已安装 Node.js（{@code npx} 可用）。
 *
 * <p>运行：
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.mcp.McpDemo"
 * </pre>
 */
public class McpDemo {

    /** 使用 MCP 官方测试 server，包含 echo / add 等简单工具，无需额外配置。 */
    private static final String[] SERVER_CMD = {"npx", "-y", "@modelcontextprotocol/server-everything"};

    public static void main(String[] args) throws Exception {
        demoListTools();
        demoDirectCall();
        demoAgentWithMcpTools();
        System.exit(0);
    }

    // ── Scenario 1: 列出工具 ──────────────────────────────────────────────────

    private static void demoListTools() throws Exception {
        printHeader("1. 列出远程 MCP server 的工具");

        try (McpClientConnector mcp = McpClientConnector.stdio(SERVER_CMD[0],
                tail(SERVER_CMD))) {

            List<Tool> tools = mcp.listTools();
            System.out.printf("共 %d 个工具：%n", tools.size());
            tools.forEach(t -> System.out.printf("  %-30s %s%n", "[" + t.name() + "]", t.description()));
        }
        System.out.println();
    }

    // ── Scenario 2: 直接调用远程工具（无 LLM）────────────────────────────────

    private static void demoDirectCall() throws Exception {
        printHeader("2. 直接调用远程工具（无 LLM）");

        try (McpClientConnector mcp = McpClientConnector.stdio(SERVER_CMD[0],
                tail(SERVER_CMD))) {

            List<Tool> tools = mcp.listTools();

            // echo 工具：原样返回输入文本
            tools.stream().filter(t -> "echo".equals(t.name())).findFirst().ifPresent(echo -> {
                String result = echo.execute(Map.of("message", "Hello from hello-agents-java!"));
                System.out.println("echo(\"Hello from hello-agents-java!\") → " + result);
            });

            // add 工具：将两个数字相加
            tools.stream().filter(t -> "add".equals(t.name())).findFirst().ifPresent(add -> {
                String result = add.execute(Map.of("a", "1234", "b", "5678"));
                System.out.println("add(1234, 5678) → " + result);
            });
        }
        System.out.println();
    }

    // ── Scenario 3: Agent 使用 MCP 工具 ──────────────────────────────────────

    private static void demoAgentWithMcpTools() throws Exception {
        printHeader("3. ReActAgent 使用 MCP 工具");

        try (McpClientConnector mcp = McpClientConnector.stdio(SERVER_CMD[0],
                tail(SERVER_CMD))) {

            var llm   = OpenAiClient.fromEnv();
            var agent = new ReActAgent(llm);
            mcp.listTools().forEach(agent::addTool);

            System.out.printf("已加载 %d 个 MCP 工具%n", agent.listTools().size());

            String task = "先用 echo 工具发送消息 'MCP is working'，再用 add 工具计算 999 + 1。";
            printTask(task);
            System.out.println(agent.run(task));
        }
        System.out.println();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static String[] tail(String[] arr) {
        String[] rest = new String[arr.length - 1];
        System.arraycopy(arr, 1, rest, 0, rest.length);
        return rest;
    }

    private static void printHeader(String title) {
        System.out.println("=".repeat(55));
        System.out.println(" " + title);
        System.out.println("=".repeat(55));
    }

    private static void printTask(String task) {
        System.out.println("Task : " + task);
        System.out.print("Reply: ");
    }
}