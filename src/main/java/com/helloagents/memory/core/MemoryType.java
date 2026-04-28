package com.helloagents.memory.core;

/**
 * Persistent agent memory types. Each type survives across sessions.
 *
 * <p>All four types share one underlying store; session-scoped working memory is
 * out of scope for this system.
 */
public enum MemoryType {

    /** User preferences: code style, response verbosity, tool-chain choices. */
    USER("用户偏好", "代码风格、响应详略、工具链偏好等长期用户习惯"),

    /** Explicit corrections and validated effective approaches. */
    FEEDBACK("用户纠正", "明确纠正过的错误，或已验证有效的做法"),

    /** Non-obvious project conventions and design rationale that cannot be derived from code. */
    PROJECT("项目约定", "无法从代码直接推导的设计决策、合规原因、团队规则"),

    /** Pointers to external resources: issue boards, dashboards, doc URLs. */
    REFERENCE("外部资源", "问题单看板、监控面板地址、文档 URL 等外部指针");

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
                + ". Valid types: user, feedback, project, reference");
    }
}