package com.helloagents.memory.core;

/**
 * Cognitive memory types, each with different persistence and capacity characteristics.
 */
public enum MemoryType {

    /** Immediate sensory input; ultra-short-lived, replaced by new perceptions instantly. */
    PERCEPTUAL("感知记忆", "即时输入，极短期"),

    /** Active task context; limited capacity, cleared when task changes. */
    WORKING("工作记忆", "当前任务临时上下文，容量有限"),

    /** Personal experiences and events; time-stamped episodes. */
    EPISODIC("情节记忆", "过去的经历和事件"),

    /** Facts, rules, and general knowledge; long-term and stable. */
    SEMANTIC("语义记忆", "知识、事实、规则，长期稳定");

    public final String displayName;
    public final String description;

    MemoryType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public static MemoryType fromString(String value) {
        for (MemoryType t : values()) {
            if (t.name().equalsIgnoreCase(value)) return t;
        }
        throw new IllegalArgumentException("Unknown memory type: " + value
                + ". Valid types: perceptual, working, episodic, semantic");
    }
}