package com.helloagents.agents;

import com.helloagents.core.BaseAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolRegistry;

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
 * The loop continues until the LLM emits {@code Action: Finish[]}, with the final answer in the Thought.
 */
public class ReActAgent implements BaseAgent {

    private static final int MAX_STEPS = 10;

    private static final String SYSTEM_PROMPT = """
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
            Pattern.compile("Action:\\s*(\\w+)\\[(.*)\\]", Pattern.DOTALL);
    private static final Pattern FINISH_PATTERN =
            Pattern.compile("Action:\\s*Finish\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern THOUGHT_PATTERN =
            Pattern.compile("Thought:\\s*(.+?)(?=\\nAction:|$)", Pattern.DOTALL);

    private final LlmClient llm;
    private final ToolRegistry tools;

    public ReActAgent(LlmClient llm, ToolRegistry tools) {
        this.llm = llm;
        this.tools = tools;
    }

    @Override
    public String run(String task) {
        List<Message> history = buildHistory(task);

        for (int step = 0; step < MAX_STEPS; step++) {
            String response = llm.chat(history);
            history.add(Message.assistant(response));

            if (FINISH_PATTERN.matcher(response).find()) {
                Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(response);
                return thoughtMatcher.find() ? thoughtMatcher.group(1).strip() : response;
            }

            Matcher actionMatcher = ACTION_PATTERN.matcher(response);
            if (actionMatcher.find()) {
                String observation = tools.execute(
                        actionMatcher.group(1).strip(),
                        actionMatcher.group(2).strip());
                history.add(Message.user("Observation: " + observation));
            } else {
                return response;
            }
        }

        return "Max steps reached without a final answer.";
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        List<Message> history = buildHistory(task);

        for (int step = 0; step < MAX_STEPS; step++) {
            // Stream each step's output so the user sees the thinking in real time,
            // while accumulating the full text for parsing.
            StringBuilder buf = new StringBuilder();
            llm.stream(history, token -> {
                buf.append(token);
                onToken.accept(token);
            });
            String response = buf.toString();
            history.add(Message.assistant(response));

            if (FINISH_PATTERN.matcher(response).find()) {
                return;
            }

            Matcher actionMatcher = ACTION_PATTERN.matcher(response);
            if (actionMatcher.find()) {
                String observation = tools.execute(
                        actionMatcher.group(1).strip(),
                        actionMatcher.group(2).strip());
                String observationLine = "\nObservation: " + observation + "\n";
                onToken.accept(observationLine);
                history.add(Message.user("Observation: " + observation));
            } else {
                return;
            }
        }

        onToken.accept("\nMax steps reached without a final answer.");
    }

    private List<Message> buildHistory(String task) {
        List<Message> history = new ArrayList<>();
        history.add(Message.system(SYSTEM_PROMPT.formatted(tools.describe())));
        history.add(Message.user(task));
        return history;
    }
}