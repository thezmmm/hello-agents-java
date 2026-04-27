package com.helloagents.memory.tool;

import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.tools.Tool;
import com.helloagents.tools.ToolParameter;
import com.helloagents.tools.ToolParameter.Param;

import java.util.Map;

/** Add a new memory entry. Input: {@code type=semantic|content=...|importance=0.9} */
public class MemoryAddTool implements Tool {

    private final MemoryService service;

    public MemoryAddTool(MemoryService service) {
        this.service = service;
    }

    @Override public String name() { return "memory_add"; }

    @Override
    public String description() {
        return "Store a new memory entry and return its ID. Use the ID later to update or remove it.";
    }

    @Override
    public ToolParameter parameters() {
        return ToolParameter.of(
            Param.required("type",       "Memory type: perceptual | working | episodic | semantic", "string"),
            Param.required("content",    "The content to remember", "string"),
            Param.optional("importance", "Relevance weight 0.0-1.0, default 0.5", "number"),
            Param.optional("file_path",  "File path for perceptual memories (image, audio, video, document)", "string"),
            Param.optional("modality",   "Modality hint: image | audio | video | document | text (inferred from file_path if omitted)", "string")
        );
    }

    @Override
    public String execute(String input) {
        Map<String, String> p = MemoryService.parseParams(input);
        String content = p.get("content");
        if (content == null || content.isBlank()) return "Error: 'content' is required.";

        MemoryType type;
        try {
            type = MemoryType.fromString(p.getOrDefault("type", "working"));
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }

        double importance = MemoryService.parseDouble(p.get("importance"), 0.5);
        String filePath   = p.get("file_path");
        String modality   = p.get("modality");

        String id = service.remember(type, content, importance, filePath, modality, null);
        return "Memory added. id=%s type=%s importance=%.2f".formatted(id, type.displayName, importance);
    }
}