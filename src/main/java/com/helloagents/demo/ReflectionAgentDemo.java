package com.helloagents.demo;

import com.helloagents.agents.ReflectionAgent;
import com.helloagents.llm.OpenAiClient;

/**
 * Demo for ReflectionAgent.
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.ReflectionAgentDemo"
 * </pre>
 */
public class ReflectionAgentDemo {

    public static void main(String[] args) {
        var llm = OpenAiClient.fromEnv();
        var agent = new ReflectionAgent(llm);

        String task = "Explain the difference between a process and a thread in operating systems.";
        System.out.println("Task: " + task);
        System.out.println("Answer:");
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println();
    }
}
