package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolParameter.Param;

import java.util.Map;

/**
 * save_memory — persist a cross-session memory entry.
 *
 * <p>Call ONLY when the user explicitly states something that is valuable beyond the current
 * session AND cannot be re-derived from the current codebase or git history.
 * Do NOT call for current task progress, code structure, or temporary context.
 */
public class MemoryAddTool implements Tool {

    private final MemoryService service;

    public MemoryAddTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "save_memory"; }

    @Override
    public String description() {
        return """
                Save a memory entry that should persist across sessions. \
                Call ONLY when the information is long-term valuable and cannot be derived \
                from the current code or history (e.g. user preferences, explicit corrections, \
                non-obvious project constraints, external resource pointers). \
                Do NOT call for current task progress, file structures, or anything re-derivable.""";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            Param.required("name",        "Short slug for the index, e.g. prefer_tabs", "string"),
            Param.required("description", "One-liner summary shown in the index, e.g. User prefers tabs", "string"),
            Param.required("type",        "Memory type: user | feedback | project | reference", "string"),
            Param.required("content",     "Full memory content", "string")
        );
    }

    @Override
    public String execute(Map<String, String> p) {
        String name    = p.get("name");
        String desc    = p.get("description");
        String content = p.get("content");
        if (name    == null || name.isBlank())    return "Error: 'name' is required.";
        if (desc    == null || desc.isBlank())    return "Error: 'description' is required.";
        if (content == null || content.isBlank()) return "Error: 'content' is required.";

        MemoryType type;
        try {
            type = MemoryType.fromString(p.getOrDefault("type", "feedback"));
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }

        String id = service.remember(type, content, name, desc, null);
        return "Memory saved. id=%s name=%s type=%s".formatted(id, name, type.displayName);
    }
}