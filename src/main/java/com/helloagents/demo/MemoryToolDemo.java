package com.helloagents.demo;

import com.helloagents.memory.tool.MemoryToolkit;
import com.helloagents.tools.ToolRegistry;

/**
 * Demonstrates all nine memory tools registered via {@link MemoryToolkit}.
 */
public class MemoryToolDemo {

    public static void main(String[] args) {
        MemoryToolkit kit = new MemoryToolkit();
        ToolRegistry registry = new ToolRegistry();
        kit.registerAll(registry);

        System.out.println("=== Registered tools ===");
        registry.list().forEach(name -> System.out.println("  " + name));

        // add
        System.out.println("\n=== memory_add ===");
        System.out.println(registry.execute("memory_add", "type=semantic|content=Java runs on the JVM.|importance=0.9"));
        System.out.println(registry.execute("memory_add", "type=episodic|content=User asked about memory systems today.|importance=0.7"));
        System.out.println(registry.execute("memory_add", "type=working|content=Current task: implement memory module.|importance=0.8"));
        System.out.println(registry.execute("memory_add", "type=perceptual|content=Detected keyword: consolidate.|importance=0.4"));
        String addResult = registry.execute("memory_add", "type=perceptual|content=Detected keyword: important fact.|importance=0.6");
        System.out.println(addResult);
        String percId = addResult.split("id=")[1].split(" ")[0];

        // search
        System.out.println("\n=== memory_search ===");
        System.out.println(registry.execute("memory_search", "query=memory"));
        System.out.println(registry.execute("memory_search", "query=Java|type=semantic"));

        // summary
        System.out.println("\n=== memory_summary ===");
        System.out.println(registry.execute("memory_summary", ""));

        // stats
        System.out.println("\n=== memory_stats ===");
        System.out.println(registry.execute("memory_stats", ""));

        // update
        System.out.println("\n=== memory_update ===");
        String semanticId = registry.execute("memory_search", "query=JVM|type=semantic").lines()
                .filter(l -> l.startsWith("[")).map(l -> l.substring(1, l.indexOf("|")))
                .findFirst().orElse("unknown");
        System.out.println(registry.execute("memory_update",
                "id=" + semanticId + "|content=Java compiles to bytecode and runs on the JVM.|importance=0.95"));

        // remove
        System.out.println("\n=== memory_remove ===");
        System.out.println(registry.execute("memory_remove", percId));

        // consolidate
        System.out.println("\n=== memory_consolidate ===");
        registry.execute("memory_add", "type=perceptual|content=Key insight: agent memory mirrors human cognition.|importance=0.8");
        registry.execute("memory_add", "type=working|content=Design pattern: unified entry dispatch.|importance=0.75");
        System.out.println(registry.execute("memory_consolidate", ""));

        // forget
        System.out.println("\n=== memory_forget lru ===");
        System.out.println(registry.execute("memory_forget", "strategy=lru|count=1"));
        System.out.println("\n=== memory_forget lowest_importance ===");
        System.out.println(registry.execute("memory_forget", "strategy=lowest_importance|count=1"));

        // final stats
        System.out.println("\n=== memory_stats (final) ===");
        System.out.println(registry.execute("memory_stats", ""));

        // clear
        System.out.println("\n=== memory_clear ===");
        System.out.println(registry.execute("memory_clear", ""));
        System.out.println(registry.execute("memory_stats", ""));
    }
}