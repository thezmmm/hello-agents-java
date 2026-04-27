package com.helloagents.tools;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * A meta-tool that lets an agent load {@link Toolkit}s on demand at runtime.
 *
 * <p>Register this tool once at startup; toolkits themselves are only instantiated
 * (and their tools registered) when the agent explicitly requests them.
 *
 * <p>Example setup:
 * <pre>
 *   ToolRegistry registry = new ToolRegistry();
 *   registry.register(new ToolkitLoaderTool(registry)
 *       .addAvailable("memory", MemoryToolkit::new)
 *       .addAvailable("rag",    () -> new RagToolkit(embeddingModel, kb, llm)));
 * </pre>
 *
 * <p>Agent usage (via tool call):
 * <pre>
 *   load_toolkit[list]          → lists available toolkits
 *   load_toolkit[memory]        → loads and registers all memory tools
 * </pre>
 */
public class ToolkitLoaderTool implements Tool {

    private final ToolRegistry registry;
    private final Map<String, Supplier<Toolkit>> available = new LinkedHashMap<>();
    private final Set<String> loaded = new HashSet<>();

    public ToolkitLoaderTool(ToolRegistry registry) {
        this.registry = registry;
    }

    /**
     * Registers a toolkit factory under the given name.
     * The factory is called lazily — only when the agent requests that toolkit.
     *
     * @param name    identifier the agent uses to request this toolkit
     * @param factory supplier that constructs the {@link Toolkit} on first use
     * @return {@code this} for chaining
     */
    public ToolkitLoaderTool addAvailable(String name, Supplier<Toolkit> factory) {
        available.put(name, factory);
        return this;
    }

    @Override
    public String name() {
        return "load_toolkit";
    }

    @Override
    public String description() {
        return "Add more tools at runtime by loading a toolkit by name. "
             + "Pass 'list' to see what toolkits are available before loading one.";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            ToolParameter.Param.required("toolkit",
                "Name of the toolkit to load (e.g. 'memory', 'rag'), or 'list' to enumerate options",
                "string")
        );
    }

    @Override
    public String execute(String input) {
        String cmd = input == null ? "" : input.strip();
        if (cmd.isBlank() || cmd.equalsIgnoreCase("list")) {
            return describeAvailable();
        }
        return load(cmd);
    }

    // -------------------------------------------------------------------------

    private String describeAvailable() {
        if (available.isEmpty()) return "No toolkits available.";
        return "Available toolkits:\n" + available.keySet().stream()
                .map(n -> "- " + n + (loaded.contains(n) ? " [already loaded]" : ""))
                .collect(Collectors.joining("\n"));
    }

    private String load(String name) {
        if (!available.containsKey(name)) {
            return "Unknown toolkit '%s'. Available: %s"
                    .formatted(name, String.join(", ", available.keySet()));
        }
        if (loaded.contains(name)) {
            return "Toolkit '%s' is already loaded.".formatted(name);
        }

        Toolkit toolkit = available.get(name).get();
        toolkit.registerAll(registry);
        loaded.add(name);

        String toolNames = toolkit.getTools().stream()
                .map(Tool::name)
                .collect(Collectors.joining(", "));
        return "Loaded toolkit '%s': %s\nNew tools now available: %s"
                .formatted(toolkit.name(), toolkit.description(), toolNames);
    }
}