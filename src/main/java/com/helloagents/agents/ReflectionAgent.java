package com.helloagents.agents;

import com.helloagents.core.AbstractAgent;
import com.helloagents.core.ToolSupport;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolCall;
import com.helloagents.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reflection agent: generates an initial response and then critiques + refines it.
 *
 * <p>Pattern:
 * <ol>
 *   <li>Generate — produce an initial answer (tool calls supported here)</li>
 *   <li>Reflect  — critique the answer for accuracy, completeness, and clarity</li>
 *   <li>Refine   — produce an improved answer based on the critique</li>
 * </ol>
 *
 * <p>Construction:
 * <pre>
 *   // minimal
 *   new ReflectionAgent(llm)
 *
 *   // with custom name and generate prompt
 *   new ReflectionAgent("MyAgent", llm, systemPrompt)
 * </pre>
 */
public class ReflectionAgent extends AbstractAgent implements ToolSupport {

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
    private final String    systemPrompt;  // overrides DEFAULT_GENERATE_SYSTEM when set
    private ToolRegistry    toolRegistry;  // lazily initialised on first addTool()

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
        String critique = critiqueBuf.toString();

        onToken.accept("\n\nFinal Answer: \n");
        StringBuilder refineBuf = new StringBuilder();
        llm.stream(refineMessages(task, draft, critique), token -> {
            refineBuf.append(token);
            onToken.accept(token);
        });

        addMessage(Message.user(task));
        addMessage(Message.assistant(refineBuf.toString()));
    }

    // --- internal ------------------------------------------------------------

    private String streamGenerate(String task, Consumer<String> onToken) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(buildGeneratePrompt()));
        messages.add(Message.user(task));

        if (!hasTools()) {
            StringBuilder buf = new StringBuilder();
            llm.stream(messages, token -> { buf.append(token); onToken.accept(token); });
            return buf.toString();
        }

        for (int i = 0; i < DEFAULT_MAX_TOOL_ITERATIONS; i++) {
            StringBuilder buf = new StringBuilder();
            llm.stream(messages, token -> { buf.append(token); onToken.accept(token); });
            String response = buf.toString();
            List<ToolCall> toolCalls = toolRegistry.parseToolCalls(response);
            if (toolCalls.isEmpty()) return response;

            String clean = stripToolCalls(response, toolCalls);
            messages.add(Message.assistant(clean));
            for (ToolCall call : toolCalls) {
                String observation = "Observation: " + toolRegistry.execute(call.toolName(), call.parameters());
                onToken.accept("\n" + observation + "\n");
                messages.add(Message.user(observation));
            }
        }
        StringBuilder buf = new StringBuilder();
        llm.stream(messages, token -> { buf.append(token); onToken.accept(token); });
        return buf.toString();
    }

    private String generate(String task) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(buildGeneratePrompt()));
        messages.add(Message.user(task));

        if (!hasTools()) {
            return llm.chat(messages);
        }

        for (int i = 0; i < DEFAULT_MAX_TOOL_ITERATIONS; i++) {
            String response = llm.chat(messages);
            List<ToolCall> toolCalls = toolRegistry.parseToolCalls(response);
            if (toolCalls.isEmpty()) return response;

            String clean = stripToolCalls(response, toolCalls);
            messages.add(Message.assistant(clean));
            for (ToolCall call : toolCalls) {
                messages.add(Message.user("Observation: " + toolRegistry.execute(call.toolName(), call.parameters())));
            }
        }
        return llm.chat(messages);
    }

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

    private String buildGeneratePrompt() {
        if (!hasTools()) return systemPrompt;
        return systemPrompt + """


                ## Available Tools
                You can use the following tools to help answer questions:
                """ + toolRegistry.describe() + """


                ## Tool Call Format
                `[TOOL_CALL:tool_name:parameters]`
                Tool results will be provided automatically. You may then continue your answer.
                """;
    }

    private String stripToolCalls(String response, List<ToolCall> toolCalls) {
        String clean = response;
        for (ToolCall call : toolCalls) clean = clean.replace(call.original(), "");
        return clean.strip();
    }

    // --- tool management -----------------------------------------------------

    public void addTool(Tool tool) {
        if (toolRegistry == null) toolRegistry = new ToolRegistry();
        toolRegistry.register(tool);
    }

    public boolean hasTools() {
        return toolRegistry != null && toolRegistry.hasTools();
    }

    public boolean removeTool(String toolName) {
        return toolRegistry != null && toolRegistry.unregister(toolName);
    }

    public List<String> listTools() {
        return toolRegistry == null ? List.of() : toolRegistry.list();
    }
}
