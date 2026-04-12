package com.helloagents.tools;

/**
 * Represents a single tool-call parsed from an LLM response.
 *
 * <p>Format in LLM output: {@code [TOOL_CALL:tool_name:parameters]}
 *
 * @param toolName   name of the tool to invoke
 * @param parameters raw parameter string passed to the tool
 * @param original   the original matched text (used for string replacement)
 */
public record ToolCall(String toolName, String parameters, String original) {}
