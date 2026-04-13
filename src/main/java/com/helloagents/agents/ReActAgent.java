package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct (Reasoning + Acting) agent.
 *
 * <p>Implements the ReAct loop described in <em>ReAct: Synergizing Reasoning and Acting in
 * Language Models</em> (Yao et al., 2022). Each iteration the LLM produces:
 * <ol>
 *   <li>Thought — reasoning about what to do next</li>
 *   <li>Action — tool call in the form {@code Action: tool_name[input]}</li>
 *   <li>Observation — result of executing the tool</li>
 * </ol>
 * The loop continues until the LLM emits {@code Action: Finish}, with the final answer in the Thought.
 *
 * <p>Construction:
 * <pre>
 *   // minimal
 *   new ReActAgent(llm, toolRegistry)
 *
 *   // full
 *   new ReActAgent("MyAgent", llm, toolRegistry, systemPrompt, customPrompt, 5)
 * </pre>
 */
public class ReActAgent extends AbstractAgent {

    private static final String DEFAULT_NAME       = "ReActAgent";
    private static final int    DEFAULT_MAX_STEPS  = 10;

    /**
     * Default ReAct prompt template. {@code %s} is replaced with tool descriptions at runtime.
     * Pass a {@code customPrompt} to the full constructor to override this entirely.
     */
    private static final String DEFAULT_REACT_PROMPT = """
            You are a reasoning agent that solves tasks step by step using available tools.

            At each step, respond in this exact format:
            Thought: <your reasoning about what to do next>
            Action: <tool_name>[<input>]

            When you have enough information to answer the user, respond with:
            Thought: <your final answer>
            Action: Finish

            Available tools:
            %s
            """;

    private static final Pattern ACTION_PATTERN =
            Pattern.compile("Action:\\s*(\\w+)\\[(.*)]", Pattern.DOTALL);
    private static final Pattern FINISH_PATTERN =
            Pattern.compile("Action:\\s*Finish\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern THOUGHT_PATTERN =
            Pattern.compile("Thought:\\s*(.+?)(?=\\nAction:|$)", Pattern.DOTALL);

    private final String      agentName;
    private final LlmClient   llm;
    private final String      systemPrompt;  // optional context prepended before the ReAct prompt
    private final String      customPrompt;  // replaces the default ReAct prompt when non-null
    private final int         maxSteps;

    // --- constructors --------------------------------------------------------

    public ReActAgent(LlmClient llm) {
        this(DEFAULT_NAME, llm, null, null, DEFAULT_MAX_STEPS);
    }

    public ReActAgent(LlmClient llm, int maxSteps) {
        this(DEFAULT_NAME, llm, null, null, maxSteps);
    }

    public ReActAgent(String name, LlmClient llm, String systemPrompt, String customPrompt, int maxSteps) {
        this.agentName    = (name != null && !name.isBlank()) ? name : DEFAULT_NAME;
        this.llm          = llm;
        this.systemPrompt = (systemPrompt != null && !systemPrompt.isBlank()) ? systemPrompt : null;
        this.customPrompt = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : null;
        this.maxSteps     = maxSteps > 0 ? maxSteps : DEFAULT_MAX_STEPS;
    }

    @Override
    public String name() {
        return agentName;
    }

    // --- run / stream --------------------------------------------------------

    @Override
    public String run(String task) {
        List<Message> workingHistory = buildMessages(task);

        for (int step = 0; step < maxSteps; step++) {
            String response = llm.chat(workingHistory);
            workingHistory.add(Message.assistant(response));

            if (FINISH_PATTERN.matcher(response).find()) {
                Matcher m = THOUGHT_PATTERN.matcher(response);
                String answer = m.find() ? m.group(1).strip() : response;
                addMessage(Message.user(task));
                addMessage(Message.assistant(answer));
                return answer;
            }

            Matcher actionMatcher = ACTION_PATTERN.matcher(response);
            if (actionMatcher.find()) {
                String observation = executeTool(
                        actionMatcher.group(1).strip(),
                        actionMatcher.group(2).strip());
                workingHistory.add(Message.user("Observation: " + observation));
            } else {
                addMessage(Message.user(task));
                addMessage(Message.assistant(response));
                return response;
            }
        }

        String fallback = "Max steps reached without a final answer.";
        addMessage(Message.user(task));
        addMessage(Message.assistant(fallback));
        return fallback;
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        List<Message> workingHistory = buildMessages(task);
        StringBuilder fullOutput = new StringBuilder();

        for (int step = 0; step < maxSteps; step++) {
            StringBuilder buf = new StringBuilder();
            llm.stream(workingHistory, token -> {
                buf.append(token);
                fullOutput.append(token);
                onToken.accept(token);
            });
            String response = buf.toString();
            workingHistory.add(Message.assistant(response));

            if (FINISH_PATTERN.matcher(response).find()) {
                addMessage(Message.user(task));
                addMessage(Message.assistant(fullOutput.toString()));
                return;
            }

            Matcher actionMatcher = ACTION_PATTERN.matcher(response);
            if (actionMatcher.find()) {
                String observation = executeTool(
                        actionMatcher.group(1).strip(),
                        actionMatcher.group(2).strip());
                String observationLine = "\nObservation: " + observation + "\n";
                fullOutput.append(observationLine);
                onToken.accept(observationLine);
                workingHistory.add(Message.user("Observation: " + observation));
            } else {
                addMessage(Message.user(task));
                addMessage(Message.assistant(fullOutput.toString()));
                return;
            }
        }

        String fallback = "\nMax steps reached without a final answer.";
        fullOutput.append(fallback);
        onToken.accept(fallback);
        addMessage(Message.user(task));
        addMessage(Message.assistant(fullOutput.toString()));
    }

    // --- internal ------------------------------------------------------------

    private List<Message> buildMessages(String task) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(buildSystemPrompt()));
        messages.addAll(getHistory());          // inject conversation history
        messages.add(Message.user(task));
        return messages;
    }

    private String buildSystemPrompt() {
        String toolsDesc = hasTools() ? toolRegistry.describe() : "(none)";
        String reactPrompt = customPrompt != null
                ? (customPrompt.contains("%s") ? customPrompt.formatted(toolsDesc)
                                               : customPrompt + "\n\nAvailable tools:\n" + toolsDesc)
                : DEFAULT_REACT_PROMPT.formatted(toolsDesc);

        return systemPrompt != null ? systemPrompt + "\n\n" + reactPrompt : reactPrompt;
    }

    private String executeTool(String toolName, String input) {
        if (!hasTools()) return "Error: no tools registered.";
        return toolRegistry.execute(toolName, input);
    }

}
