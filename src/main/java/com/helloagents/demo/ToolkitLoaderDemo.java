package com.helloagents.demo;

import com.helloagents.agents.ReActAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.memory.tool.MemoryToolkit;
import com.helloagents.tools.CalculatorTool;
import com.helloagents.tools.ToolRegistry;
import com.helloagents.tools.ToolkitLoaderTool;

/**
 * 演示 ToolkitLoaderTool：Agent 按需加载 Toolkit
 *
 * <p>场景：
 * <ol>
 *   <li>机制演示 — 不调用 LLM，直接演示 ToolkitLoaderTool 的加载和幂等行为</li>
 *   <li>Agent 集成 — ReActAgent 仅持有 calculate + load_toolkit 两个工具，
 *       由 LLM 在 Thought/Action 循环中自行判断何时加载 memory toolkit 并使用其工具</li>
 * </ol>
 *
 * <p>运行：
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.ToolkitLoaderDemo"
 * </pre>
 */
public class ToolkitLoaderDemo {

    public static void main(String[] args) {
        scenario1_mechanism();
        System.out.println();
        scenario2_agent();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 场景 1：纯机制演示（不需要 LLM）
    // ─────────────────────────────────────────────────────────────────────────
    static void scenario1_mechanism() {
        System.out.println("=== 场景 1：ToolkitLoaderTool 机制演示 ===\n");

        ToolRegistry registry = new ToolRegistry();
        registry.register(new CalculatorTool());
        registry.register(new ToolkitLoaderTool(registry)
                .addAvailable("memory", MemoryToolkit::new));

        // 初始状态：只有 calculate + load_toolkit
        System.out.println("【初始工具列表】");
        registry.list().forEach(t -> System.out.println("  " + t));

        // 查询可用的 toolkit
        System.out.println("\n【查询可用 Toolkit】");
        System.out.println(registry.execute("load_toolkit", "{\"toolkit\":\"list\"}"));

        // 加载 memory toolkit
        System.out.println("\n【加载 memory toolkit】");
        System.out.println(registry.execute("load_toolkit", "{\"toolkit\":\"memory\"}"));

        // 加载后工具列表应该扩展
        System.out.println("\n【加载后工具列表】");
        registry.list().forEach(t -> System.out.println("  " + t));

        // 重复加载应该幂等
        System.out.println("\n【重复加载（应幂等）】");
        System.out.println(registry.execute("load_toolkit", "{\"toolkit\":\"memory\"}"));

        // 直接使用刚加载的 memory 工具
        System.out.println("\n【使用刚加载的工具】");
        String addResult = registry.execute("memory_add",
                "{\"type\":\"semantic\",\"content\":\"The speed of light is 299792458 m/s.\",\"importance\":0.95}");
        System.out.println("memory_add   → " + addResult);
        System.out.println("memory_search → "
                + registry.execute("memory_search", "{\"query\":\"light\"}"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 场景 2：ReActAgent + ToolkitLoaderTool（需要配置 LLM 环境变量）
    //
    // ReActAgent 的 Thought/Action 格式强制 LLM 在每步行动前显式推理，
    // 适合需要多步骤、跨 toolkit 的复合任务。
    //
    // Agent 启动时只有 calculate 和 load_toolkit，
    // 任务需要 memory_add 和 memory_search（需先通过 load_toolkit 加载）。
    // ─────────────────────────────────────────────────────────────────────────
    static void scenario2_agent() {
        System.out.println("=== 场景 2：ReActAgent 按需加载 memory toolkit ===\n");

        LlmClient llm = LlmClient.fromEnv();

        // ReActAgent 通过 addTool() 初始化内部 toolRegistry。
        // ToolkitLoaderTool 持有同一 registry 引用，加载时直接注册进去，
        // agent 下一步即可解析和执行新工具。
        ReActAgent agent = new ReActAgent("DynamicAgent", llm, null, null, 10);
        agent.addTool(new CalculatorTool());
        agent.addTool(new ToolkitLoaderTool(agent.getToolRegistry())
                .addAvailable("memory", MemoryToolkit::new));

        System.out.println("【Agent 初始工具】");
        agent.listTools().forEach(t -> System.out.println("  " + t));

        String task = """
                Complete these steps in order:
                1. Calculate 17 * 24.
                2. Store the result as a semantic memory:
                   content = "17 * 24 = <the result>", importance = 0.9.
                3. Search memories for "17 * 24" and report what you find.
                """;

        System.out.println("\n【任务】\n" + task.strip());
        System.out.println("\n【Agent 执行过程】");
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });

        System.out.println("\n\n【Agent 执行后工具列表】");
        agent.listTools().forEach(t -> System.out.println("  " + t));
    }
}
