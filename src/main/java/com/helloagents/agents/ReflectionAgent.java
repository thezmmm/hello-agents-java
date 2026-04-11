package com.helloagents.agents;

import com.helloagents.core.BaseAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.llm.Message;

import java.util.List;
import java.util.function.Consumer;

/**
 * Reflection agent: generates an initial response and then critiques + refines it.
 *
 * <p>Pattern:
 * <ol>
 *   <li>Generate — produce an initial answer</li>
 *   <li>Reflect — critique the answer for accuracy, completeness, and clarity</li>
 *   <li>Refine — produce an improved answer based on the critique</li>
 * </ol>
 */
public class ReflectionAgent implements BaseAgent {

    private static final String GENERATE_SYSTEM = """
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

    private final LlmClient llm;

    public ReflectionAgent(LlmClient llm) {
        this.llm = llm;
    }

    @Override
    public String run(String task) {
        String draft    = generate(task);
        String critique = reflect(task, draft);
        return llm.chat(refineMessages(task, draft, critique));
    }

    @Override
    public void stream(String task, Consumer<String> onToken) {
        String draft    = generate(task);
        String critique = reflect(task, draft);
        llm.stream(refineMessages(task, draft, critique), onToken);
    }

    private String generate(String task) {
        return llm.chat(List.of(
                Message.system(GENERATE_SYSTEM),
                Message.user(task)));
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
}