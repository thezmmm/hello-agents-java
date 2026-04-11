package com.helloagents.tools;

/**
 * A tool that an agent can call by name with a string input.
 */
public interface Tool {

    /** Unique name used by the agent to invoke this tool. */
    String name();

    /** One-line description shown to the LLM in the system prompt. */
    String description();

    /**
     * Execute the tool with the given input string.
     *
     * @param input raw input from the LLM
     * @return result string fed back to the LLM as an Observation
     */
    String execute(String input);
}
