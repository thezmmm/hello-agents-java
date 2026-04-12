package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolCall;
import com.helloagents.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A general-purpose conversational agent with optional tool-calling support.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Conversation history is automatically prepended to every call.</li>
 *   <li>When a {@link ToolRegistry} is provided, the system prompt is enhanced with tool
 *       descriptions and the agent enters a multi-iteration tool-call loop.</li>
 *   <li>Tool calls are expressed inline as {@code [TOOL_CALL:tool_name:parameters]};
 *       results are fed back as {@code tool} role messages.</li>
 * </ul>
 */
public class SimpleAgent extends AbstractAgent {

    private static final String DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant. Answer the user's question concisely and accurately.";

    private static final int DEFAULT_MAX_TOOL_ITERATIONS = 3;

    private final String agentName;
    private final LlmClient llm;
    private final String systemPrompt;
    private ToolRegistry toolRegistry;        // lazily initialised on first addTool()

    // --- constructors --------------------------------------------------------

    public SimpleAgent(LlmClient llm) {
        this("SimpleAgent", llm, DEFAULT_SYSTEM_PROMPT, null);
    }

    public SimpleAgent(LlmClient llm, String systemPrompt) {
        this("SimpleAgent", llm, systemPrompt, null);
    }

    public SimpleAgent(String name, LlmClient llm, String systemPrompt, ToolRegistry toolRegistry) {
        this.agentName       = name;
        this.llm             = llm;
        this.systemPrompt    = systemPrompt != null ? systemPrompt : DEFAULT_SYSTEM_PROMPT;
        this.toolRegistry = toolRegistry;
    }

    @Override
    public String name() {
        return agentName;
    }

    // --- BaseAgent -----------------------------------------------------------

    @Override
    public String run(String task) {
        return run(task, DEFAULT_MAX_TOOL_ITERATIONS);
    }

    /**
     * Runs the agent with a configurable tool-iteration limit.
     *
     * @param task              natural-language task
     * @param maxToolIterations maximum number of tool-call rounds before forcing a final answer
     */
    public String run(String task, int maxToolIterations) {
        List<Message> messages = buildMessages(task);

        String response;
        if (hasTools()) {
            response = runWithTools(messages, task, maxToolIterations);
        } else {
            response = llm.chat(messages);
            addMessage(Message.user(task));
            addMessage(Message.assistant(response));
        }
        return response;
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        stream(task, DEFAULT_MAX_TOOL_ITERATIONS, onToken);
    }

    public void stream(String task, int maxToolIterations, Consumer<String> onToken) {
        List<Message> messages = buildMessages(task);

        if (!hasTools()) {
            StringBuilder buf = new StringBuilder();
            llm.stream(messages, token -> {
                buf.append(token);
                onToken.accept(token);
            });
            addMessage(Message.user(task));
            addMessage(Message.assistant(buf.toString()));
            return;
        }

        streamWithTools(messages, task, maxToolIterations, onToken);
    }

    // --- tool-calling loop ---------------------------------------------------

    private void streamWithTools(List<Message> messages, String task, int maxToolIterations,
                                 Consumer<String> onToken) {
        String finalResponse = "";

        for (int iteration = 0; iteration < maxToolIterations; iteration++) {
            StringBuilder buf = new StringBuilder();
            llm.stream(messages, token -> {
                buf.append(token);
                onToken.accept(token);
            });
            String response = buf.toString();
            List<ToolCall> toolCalls = extractToolCalls(response);

            if (toolCalls.isEmpty()) {
                finalResponse = response;
                break;
            }

            // strip markers and record the clean assistant turn
            String cleanResponse = response;
            for (ToolCall call : toolCalls) {
                cleanResponse = cleanResponse.replace(call.original(), "");
            }
            messages.add(Message.assistant(cleanResponse.strip()));

            // execute tools, emit observations, feed back as tool messages
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolCall call = toolCalls.get(i);
                String callId = "call_%s_%d_%d".formatted(call.toolName(), iteration, i);
                String result = executeToolCall(call);
                onToken.accept("\nObservation: " + result + "\n");
                messages.add(Message.tool(callId, result));
            }
        }

        if (finalResponse.isEmpty()) {
            StringBuilder buf = new StringBuilder();
            llm.stream(messages, token -> {
                buf.append(token);
                onToken.accept(token);
            });
            finalResponse = buf.toString();
        }

        addMessage(Message.user(task));
        addMessage(Message.assistant(finalResponse));
    }

    private String runWithTools(List<Message> messages, String task, int maxToolIterations) {
        String finalResponse = "";

        for (int iteration = 0; iteration < maxToolIterations; iteration++) {
            String response = llm.chat(messages);
            List<ToolCall> toolCalls = extractToolCalls(response);

            if (toolCalls.isEmpty()) {
                finalResponse = response;
                break;
            }

            String cleanResponse = response;
            for (ToolCall call : toolCalls) {
                cleanResponse = cleanResponse.replace(call.original(), "");
            }
            messages.add(Message.assistant(cleanResponse.strip()));
            for (int i = 0; i < toolCalls.size(); i++) {
                ToolCall call = toolCalls.get(i);
                String callId = "call_%s_%d_%d".formatted(call.toolName(), iteration, i);
                messages.add(Message.tool(callId, executeToolCall(call)));
            }
        }

        // if iterations exhausted without a clean answer, ask for one final reply
        if (finalResponse.isEmpty()) {
            finalResponse = llm.chat(messages);
        }

        addMessage(Message.user(task));
        addMessage(Message.assistant(finalResponse));
        return finalResponse;
    }

    // --- message building ----------------------------------------------------

    private List<Message> buildMessages(String task) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(buildSystemPrompt()));
        messages.addAll(getHistory());          // inject conversation history
        messages.add(Message.user(task));
        return messages;
    }

    private String buildSystemPrompt() {
        if (!hasTools()) return systemPrompt;

        String toolsDesc = toolRegistry.describe();
        if (toolsDesc.isBlank()) return systemPrompt;

        return systemPrompt + """


                ## Available Tools
                You can use the following tools to help answer questions:
                """ + toolsDesc + """


                ## Tool Call Format
                When you need to use a tool, embed this exact tag in your response:
                `[TOOL_CALL:tool_name:parameters]`
                Example: `[TOOL_CALL:calculator:1+2*3]`

                Tool results will be provided automatically. You may then continue your answer.
                """;
    }

    // --- tool-call parsing ---------------------------------------------------

    /**
     * Extracts all {@code [TOOL_CALL:name:params]} markers from the LLM response.
     *
     * @param response raw LLM response
     * @return ordered list of parsed tool calls; empty if none found
     */
    private List<ToolCall> extractToolCalls(String response) {
        return toolRegistry.parseToolCalls(response);
    }

    // --- tool-call execution -------------------------------------------------

    /**
     * Executes a single tool call via the registry and returns the result string.
     *
     * @param call the tool call to execute
     * @return tool output, or an error message if the registry is unavailable
     */
    protected String executeToolCall(ToolCall call) {
        if (toolRegistry == null) return "Error: no tool registry configured.";
        return toolRegistry.execute(call.toolName(), call.parameters());
    }

    // --- tool management -----------------------------------------------------

    /**
     * Registers a tool with this agent. If no registry exists yet, one is created lazily.
     *
     * @param tool the tool to add
     */
    public void addTool(Tool tool) {
        if (toolRegistry == null) toolRegistry = new ToolRegistry();
        toolRegistry.register(tool);
    }

    /** Returns {@code true} if at least one tool is registered and tool-calling is active. */
    public boolean hasTools() {
        return toolRegistry != null && toolRegistry.hasTools();
    }

    /**
     * Removes the tool with the given name.
     *
     * @return {@code true} if the tool was present and removed
     */
    public boolean removeTool(String toolName) {
        return toolRegistry != null && toolRegistry.unregister(toolName);
    }

    /**
     * Returns the names of all registered tools in registration order.
     * Returns an empty list if no registry has been configured.
     */
    public List<String> listTools() {
        return toolRegistry == null ? List.of() : toolRegistry.list();
    }
}
