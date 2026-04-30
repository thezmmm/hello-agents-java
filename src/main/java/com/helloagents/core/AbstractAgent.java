package com.helloagents.core;

import com.helloagents.context.CompressedHistory;
import com.helloagents.context.SystemPromptBuilder;
import com.helloagents.llm.Message;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base implementation of {@link BaseAgent} providing conversation history,
 * execution trace, and optional context enhancement via {@link CompressedHistory}
 * and {@link SystemPromptBuilder}.
 *
 * <p>Two parallel records are maintained per run:
 * <ul>
 *   <li>{@code history} — clean user/assistant pairs only; consumed by
 *       {@link CompressedHistory} for LLM context.</li>
 *   <li>{@code executionTrace} — full message sequence per run including tool
 *       call exchanges; consumed by users and persistence layers.</li>
 * </ul>
 */
public abstract class AbstractAgent implements BaseAgent, ToolSupport {

    private final List<Message>         history        = new ArrayList<>();
    private final List<List<Message>>   executionTrace = new ArrayList<>();

    protected CompressedHistory   compressedHistory;
    protected SystemPromptBuilder systemPromptBuilder;

    /** Lazily initialised when the first tool is registered. */
    protected ToolRegistry toolRegistry;

    // ── History ───────────────────────────────────────────────────────────────

    @Override
    public void addMessage(Message message) {
        history.add(message);
    }

    @Override
    public List<Message> getHistory() {
        return Collections.unmodifiableList(history);
    }

    @Override
    public void clearHistory() {
        history.clear();
        if (compressedHistory != null) compressedHistory.clear();
    }

    // ── ExecutionTrace ────────────────────────────────────────────────────────

    @Override
    public List<List<Message>> getExecutionTrace() {
        return Collections.unmodifiableList(executionTrace);
    }

    @Override
    public void clearExecutionTrace() {
        executionTrace.clear();
    }

    protected void addExecutionTrace(List<Message> trace) {
        executionTrace.add(Collections.unmodifiableList(new ArrayList<>(trace)));
    }

    // ── Context enhancement (optional) ───────────────────────────────────────

    public AbstractAgent withCompressedHistory(CompressedHistory compressedHistory) {
        this.compressedHistory = compressedHistory;
        return this;
    }

    public AbstractAgent withSystemPromptBuilder(SystemPromptBuilder systemPromptBuilder) {
        this.systemPromptBuilder = systemPromptBuilder;
        return this;
    }

    // ── ToolSupport ───────────────────────────────────────────────────────────

    @Override
    public void addTool(Tool tool) {
        if (toolRegistry == null) toolRegistry = new ToolRegistry();
        toolRegistry.register(tool);
    }

    @Override
    public boolean hasTools() {
        return toolRegistry != null && toolRegistry.hasTools();
    }

    @Override
    public boolean removeTool(String toolName) {
        return toolRegistry != null && toolRegistry.unregister(toolName);
    }

    @Override
    public List<String> listTools() {
        return toolRegistry == null ? List.of() : toolRegistry.list();
    }

    @Override
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }
}