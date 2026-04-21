package com.helloagents.memory;

/**
 * Strategy that determines which memories are evicted during a {@code forget} operation.
 */
public enum ForgetStrategy {

    /** Least Recently Used — evict entries not accessed for the longest time. */
    LRU,

    /** Least Frequently Used — evict entries with the lowest access count. */
    LFU,

    /** Oldest first — evict entries created earliest. */
    OLDEST,

    /** Lowest importance first — evict entries with the smallest importance score. */
    LOWEST_IMPORTANCE;

    public static ForgetStrategy fromString(String value) {
        for (ForgetStrategy s : values()) {
            if (s.name().equalsIgnoreCase(value)) return s;
        }
        throw new IllegalArgumentException("Unknown forget strategy: " + value
                + ". Valid strategies: lru, lfu, oldest, lowest_importance");
    }
}