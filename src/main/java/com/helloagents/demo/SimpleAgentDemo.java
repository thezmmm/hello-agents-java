package com.helloagents.demo;

import com.helloagents.agents.SimpleAgent;
import com.helloagents.llm.OpenAiClient;

/**
 * Demo for Chapter 1 — SimpleAgent.
 *
 * <p>Run with:
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.helloagents.demo.SimpleAgentDemo"
 * </pre>
 *
 * <p>Required environment variable: {@code LLM_API_KEY}
 */
public class SimpleAgentDemo {

    public static void main(String[] args) {
        var llm = OpenAiClient.fromEnv();
        var agent = new SimpleAgent(llm);

        String task = "用一句话解释什么是智能体（Agent）。";
        System.out.println("Task: " + task);
        System.out.print("Answer: ");
        agent.stream(task, token -> {
            System.out.print(token);
            System.out.flush();
        });
        System.out.println();
    }
}
