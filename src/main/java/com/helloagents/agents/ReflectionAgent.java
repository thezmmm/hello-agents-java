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
 * Reflection agent: generates an initial response and then critiques + refines it.
 *
 * <p>Pattern:
 * <ol>
 *   <li>Generate — produce an initial answer (native function calling for tools)</li>
 *   <li>Reflect  — critique the answer for accuracy, completeness, and clarity</li>
 *   <li>Refine   — produce an improved answer based on the critique</li>
 * </ol>
 *
 * <p>Construction:
 * <pre>
 *   new ReflectionAgent(llm)
 *   new ReflectionAgent("MyAgent", llm, systemPrompt)
 * </pre>
 */
public class ReflectionAgent extends AbstractAgent {

    private static final String DEFAULT_NAME = "ReflectionAgent";
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
    public String name() {
        return agentName;
    }

    // --- run / stream --------------------------------------------------------

    @Override
    public String run(String task) {
        String draft    = generate(task);
        String critique = reflect(task, draft);
        String response = llm.chat(refineMessages(task, draft, critique));
        addMessage(Message.user(task));
        addMessage(Message.assistant(response));
        return response;
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        onToken.accept("Draft: \n");
        String draft = streamGenerate(task, onToken);

        onToken.accept("\n\nCritique: \n");
        StringBuilder critiqueBuf = new StringBuilder();
        llm.stream(List.of(
                Message.system(REFLECT_SYSTEM),
                Message.user("Question: %s\n\nDraft answer:\n%s".formatted(task, draft))),
                token -> {
                    critiqueBuf.append(token);
                    onToken.accept(token);
                });

        onToken.accept("\n\nFinal Answer: \n");
        StringBuilder refineBuf = new StringBuilder();
        llm.stream(refineMessages(task, draft, critiqueBuf.toString()), token -> {
            refineBuf.append(token);
            onToken.accept(token);
        });

        addMessage(Message.user(task));
        addMessage(Message.assistant(refineBuf.toString()));
    }

    // --- generate phase (with native function calling if tools registered) ---

    private String generate(String task) {
        List<Message> messages = List.of(Message.system(systemPrompt), Message.user(task));

        if (!hasTools()) {
            return llm.chat(messages);
        }

        List<Message> history = new ArrayList<>(messages);
        for (int i = 0; i < DEFAULT_MAX_TOOL_ITERATIONS; i++) {
            LlmResponse resp = llm.chat(history, toolRegistry.getTools());
            if (!resp.hasToolCalls()) {
                return resp.content() != null ? resp.content() : "";
            }
            history.add(Message.assistantWithToolCalls(resp.toolCalls()));
            for (FunctionCall call : resp.toolCalls()) {
                history.add(Message.tool(call.id(), toolRegistry.execute(call.name(), call.parseArguments())));
            }
        }
        return llm.chat(history);
    }

    private String streamGenerate(String task, Consumer<String> onToken) {
        List<Message> messages = List.of(Message.system(systemPrompt), Message.user(task));

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
            history.add(Message.assistantWithToolCalls(resp.toolCalls()));
            for (FunctionCall call : resp.toolCalls()) {
                String result = toolRegistry.execute(call.name(), call.parseArguments());
                onToken.accept("\n[" + call.name() + "] → " + result + "\n");
                history.add(Message.tool(call.id(), result));
            }
        }
        LlmResponse resp = llm.stream(history, toolRegistry.getTools(), onToken);
        return resp.content() != null ? resp.content() : "";
    }

    // --- reflect / refine (no tools) ----------------------------------------

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
}
