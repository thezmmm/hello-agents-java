package com.helloagents.demo;

import com.helloagents.agents.PlanAndSolveAgent;
import com.helloagents.llm.OpenAiClient;

/**
 * Demo for PlanAndSolveAgent.
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.PlanAndSolveAgentDemo"
 * </pre>
 */
public class PlanAndSolveAgentDemo {

    public static void main(String[] args) {
        var llm = OpenAiClient.fromEnv();
        var agent = PlanAndSolveAgent.of(llm);

        String task = "A fruit store sold 15 apples on Monday. The number of apples sold on Tuesday was double that of Monday. The number of apples sold on Wednesday was 5 fewer than that of Tuesday. How many apples did the store sell in total over the three days?";
        System.out.println("Task: " + task);
        System.out.println("Answer:");
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println();
    }
}
