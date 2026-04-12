package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
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
public class ReActAgent extends AbstractAgent {

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
        List<Message> workingHistory = buildWorkingHistory(task);

        for (int step = 0; step < MAX_STEPS; step++) {
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
                String observation = tools.execute(
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
        List<Message> workingHistory = buildWorkingHistory(task);
        StringBuilder fullOutput = new StringBuilder();

        for (int step = 0; step < MAX_STEPS; step++) {
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
                String observation = tools.execute(
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

    private List<Message> buildWorkingHistory(String task) {
        List<Message> workingHistory = new ArrayList<>();
        workingHistory.add(Message.system(SYSTEM_PROMPT.formatted(tools.describe())));
        workingHistory.add(Message.user(task));
        return workingHistory;
    }
}