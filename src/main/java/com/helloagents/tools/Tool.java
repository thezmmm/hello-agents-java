package com.helloagents.tools;

import java.util.Map;

/**
 * A tool that an agent can call by name with parsed parameters.
 */
public interface Tool {

    /** Unique name used by the agent to invoke this tool. */
    String name();

    /** One-line description shown to the LLM in the system prompt. */
    String description();

    /**
     * Returns the parameter schema for this tool.
     * Override to declare all accepted parameters for richer prompt generation.
     *
     * @return a {@link ToolParameter} holding all parameter definitions; empty by default
     */
    default ToolParameter parameters() {
        return ToolParameter.empty();
    }

    /**
     * Execute the tool with pre-parsed parameters.
     * Parameters are already extracted from the JSON in the LLM response by
     * {@link ToolRegistry#parseToolCalls}; implementations just call {@code params.get("key")}.
     *
     * @param params key→value map parsed from the tool call's JSON parameter block
     * @return result string fed back to the LLM as an Observation
     */
    String execute(Map<String, String> params);
}
