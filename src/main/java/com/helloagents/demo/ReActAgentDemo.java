package com.helloagents.demo;

import com.helloagents.agents.ReActAgent;
import com.helloagents.llm.OpenAiClient;
import com.helloagents.tools.CalculatorTool;
import com.helloagents.tools.ToolRegistry;

/**
 * Demo for ReActAgent with a calculator tool.
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.ReActAgentDemo"
 * </pre>
 */
public class ReActAgentDemo {

    public static void main(String[] args) {
        var llm = OpenAiClient.fromEnv();
        var tools = new ToolRegistry()
                .register(new CalculatorTool());
        var agent = new ReActAgent(llm, tools);

        String task = "If a rectangle has width 7 and height 5, what is its area? Then multiply that by 3.";
        System.out.println("Task: " + task);
        // ReActAgent uses blocking run() internally (intermediate steps need full responses for parsing).
        // stream() here emits the complete final answer as one token via the BaseAgent default.
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println();
    }
}
