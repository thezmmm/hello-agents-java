package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
import com.helloagents.llm.FunctionCall;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.LlmResponse;
import com.helloagents.llm.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reflection agent: Generate → Reflect → Refine.
 *
 * <p>The Generate phase uses the configured system prompt, conversation history,
 * and optional tool calling. Reflect and Refine phases use their own fixed system
 * prompts and do not call tools.
 */
public class ReflectionAgent extends AbstractAgent {

    private static final String DEFAULT_NAME              = "ReflectionAgent";
    private static final int    DEFAULT_MAX_TOOL_ITERATIONS = 3;

    private static final String DEFAULT_GENERATE_SYSTEM = """
            You are an expert assistant. Answer the user's question thoroughly and accurately.
            """;

    private static final String REFLECT_SYSTEM = """
            You are a critical reviewer. Given a question and a draft answer, identify:
            - Factual errors or inaccuracies
            - Missing important information
            - Clarity or structure issues
            Be concise and specific in your critique.
            """;

    private static final String REFINE_SYSTEM = """
            You are an expert assistant. You will be given a question, an initial draft answer,
            and a critique of that draft. Produce an improved final answer that addresses all
            points raised in the critique.
            """;

    private final String    agentName;
    private final LlmClient llm;
    private final String    systemPrompt;

    // --- constructors --------------------------------------------------------

    public ReflectionAgent(LlmClient llm) {
        this(DEFAULT_NAME, llm, null);
    }

    public ReflectionAgent(String name, LlmClient llm, String systemPrompt) {
        this.agentName    = (name != null && !name.isBlank()) ? name : DEFAULT_NAME;
        this.llm          = llm;
        this.systemPrompt = (systemPrompt != null && !systemPrompt.isBlank())
                ? systemPrompt : DEFAULT_GENERATE_SYSTEM;
    }

    @Override
    public String name() { return agentName; }

    // --- run -----------------------------------------------------------------

    @Override
    public String run(String task) {
        List<Message> trace = new ArrayList<>();
        trace.add(Message.user(task));

        String draft    = generate(task, trace);
        String critique = reflect(task, draft);
        String response = llm.chat(refineMessages(task, draft, critique));

        trace.add(Message.assistant(draft));
        trace.add(Message.assistant(critique));
        trace.add(Message.assistant(response));

        addMessage(Message.user(task));
        addMessage(Message.assistant(response));
        addExecutionTrace(trace);
        return response;
    }

    // --- stream --------------------------------------------------------------

    @Override
    public void stream(String task, Consumer<String> onToken) {
        List<Message> trace = new ArrayList<>();
        trace.add(Message.user(task));

        onToken.accept("Draft: \n");
        String draft = streamGenerate(task, trace, onToken);

        onToken.accept("\n\nCritique: \n");
        StringBuilder critiqueBuf = new StringBuilder();
        llm.stream(List.of(
                Message.system(REFLECT_SYSTEM),
                Message.user("Question: %s\n\nDraft answer:\n%s".formatted(task, draft))),
                token -> { critiqueBuf.append(token); onToken.accept(token); });

        onToken.accept("\n\nFinal Answer: \n");
        StringBuilder refineBuf = new StringBuilder();
        llm.stream(refineMessages(task, draft, critiqueBuf.toString()),
                token -> { refineBuf.append(token); onToken.accept(token); });

        trace.add(Message.assistant(draft));
        trace.add(Message.assistant(critiqueBuf.toString()));
        trace.add(Message.assistant(refineBuf.toString()));

        addMessage(Message.user(task));
        addMessage(Message.assistant(refineBuf.toString()));
        addExecutionTrace(trace);
    }

    // --- generate phase ------------------------------------------------------

    private String generate(String task, List<Message> trace) {
        List<Message> messages = buildGenerateMessages(task);

        if (!hasTools()) {
            return llm.chat(messages);
        }

        List<Message> history = new ArrayList<>(messages);
        for (int i = 0; i < DEFAULT_MAX_TOOL_ITERATIONS; i++) {
            LlmResponse resp = llm.chat(history, toolRegistry.getTools());
            if (!resp.hasToolCalls()) {
                return resp.content() != null ? resp.content() : "";
            }
            Message toolCallMsg = Message.assistantWithToolCalls(resp.toolCalls());
            history.add(toolCallMsg);
            trace.add(toolCallMsg);
            for (FunctionCall call : resp.toolCalls()) {
                String  result        = toolRegistry.execute(call.name(), call.parseArguments());
                Message toolResultMsg = Message.tool(call.id(), result);
                history.add(toolResultMsg);
                trace.add(toolResultMsg);
            }
        }
        return llm.chat(history);
    }

    private String streamGenerate(String task, List<Message> trace, Consumer<String> onToken) {
        List<Message> messages = buildGenerateMessages(task);

        if (!hasTools()) {
            StringBuilder buf = new StringBuilder();
            llm.stream(messages, token -> { buf.append(token); onToken.accept(token); });
            return buf.toString();
        }

        List<Message> history = new ArrayList<>(messages);
        for (int i = 0; i < DEFAULT_MAX_TOOL_ITERATIONS; i++) {
            LlmResponse resp = llm.stream(history, toolRegistry.getTools(), onToken);
            if (!resp.hasToolCalls()) {
                return resp.content() != null ? resp.content() : "";
            }
            Message toolCallMsg = Message.assistantWithToolCalls(resp.toolCalls());
            history.add(toolCallMsg);
            trace.add(toolCallMsg);
            for (FunctionCall call : resp.toolCalls()) {
                String  result        = toolRegistry.execute(call.name(), call.parseArguments());
                Message toolResultMsg = Message.tool(call.id(), result);
                onToken.accept("\n[" + call.name() + "] → " + result + "\n");
                history.add(toolResultMsg);
                trace.add(toolResultMsg);
            }
        }
        LlmResponse resp = llm.stream(history, toolRegistry.getTools(), onToken);
        return resp.content() != null ? resp.content() : "";
    }

    // --- reflect / refine ----------------------------------------------------

    private String reflect(String task, String draft) {
        return llm.chat(List.of(
                Message.system(REFLECT_SYSTEM),
                Message.user("Question: %s\n\nDraft answer:\n%s".formatted(task, draft))));
    }

    private List<Message> refineMessages(String task, String draft, String critique) {
        return List.of(
                Message.system(REFINE_SYSTEM),
                Message.user("""
                        Question: %s

                        Draft answer:
                        %s

                        Critique:
                        %s
                        """.formatted(task, draft, critique)));
    }

    // --- message building ----------------------------------------------------

    private List<Message> buildGenerateMessages(String task) {
        List<Message> messages = new ArrayList<>();

        String baseSystem = systemPromptBuilder != null
                ? systemPromptBuilder.build(task, systemPrompt)
                : systemPrompt;

        if (compressedHistory != null) {
            compressedHistory.sync(getHistory());
            String summary = compressedHistory.getSummary();
            String system  = summary != null
                    ? baseSystem + "\n\nConversation summary:\n" + summary
                    : baseSystem;
            messages.add(Message.system(system));
            messages.addAll(compressedHistory.getRecentMessages());
        } else {
            messages.add(Message.system(baseSystem));
            messages.addAll(getHistory());
        }

        messages.add(Message.user(task));
        return messages;
    }
}