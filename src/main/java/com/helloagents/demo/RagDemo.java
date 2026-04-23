package com.helloagents.demo;

import com.helloagents.agents.SimpleAgent;
import com.helloagents.llm.LlmClient;
import com.helloagents.rag.app.RagSystem;
import com.helloagents.rag.core.KnowledgeBase;
import com.helloagents.rag.embedding.OpenAiEmbeddingModel;
import com.helloagents.rag.store.InMemoryDocumentStore;
import com.helloagents.rag.store.InMemoryVectorStore;
import com.helloagents.rag.tool.RagToolkit;

/**
 * RAG 系统演示
 * 运行：mvn exec:java -Dexec.mainClass="com.helloagents.demo.RagDemo"
 */
public class RagDemo {

    public static void main(String[] args) {
        var embeddingModel = OpenAiEmbeddingModel.fromEnv();
        var llm            = LlmClient.fromEnv();

        // ── 场景 1：直接使用 RagSystem ─────────────────────────────────────
        System.out.println("=== 场景 1：RagSystem ===\n");

        var kb  = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
        var rag = new RagSystem(embeddingModel, kb, llm);

        String docId = rag.addDocument("java-intro.txt", """
                Java 是一种面向对象的编程语言，由 Sun Microsystems 于 1995 年发布。
                Java 程序运行在 JVM（Java Virtual Machine）上，支持垃圾回收机制。
                JVM 的垃圾回收器（GC）负责自动管理内存，常见算法有 G1、ZGC、Shenandoah。
                """);

        System.out.println("已索引文档 id=" + docId);
        System.out.println("文档数: " + rag.documentCount() + "  chunk数: " + rag.chunkCount());

        System.out.println("\n--- 语义搜索 ---");
        rag.search("JVM 内存管理", 2)
           .forEach(r -> System.out.printf("[%.3f] %s%n", r.score(), r.content()));

        System.out.println("\n--- RAG 问答 ---");
        System.out.println(rag.ask("Java 的垃圾回收机制是什么？"));

        // ── 场景 2：集成进 Agent ─────────────────────────────────────────────
        System.out.println("\n=== 场景 2：RagToolkit + SimpleAgent ===\n");

        var kb2     = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
        var toolkit = new RagToolkit(embeddingModel, kb2, llm);
        var agent   = new SimpleAgent(llm);
        toolkit.getTools().forEach(agent::addTool);

        System.out.println(agent.run("""
                请先将下面内容加入知识库（source=python-intro.txt）：
                Python 是一种高级编程语言，以简洁可读著称。
                Python 使用动态类型和垃圾回收机制，支持多种编程范式。
                CPython 是最常见的 Python 实现，代码运行在解释器上。

                然后回答：Python 的内存管理是如何工作的？
                """));
    }
}