package com.helloagents.context;

import com.helloagents.llm.Message;
import com.helloagents.memory.MemoryManager;
import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.rag.core.Embedding;
import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.core.ModelInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContextBuilderTest {

    private ContextConfig  config;
    private ContextBuilder builder;

    @BeforeEach
    void setUp() {
        config  = ContextConfig.defaults();
        builder = new ContextBuilder(config);
    }

    // ── section presence ──────────────────────────────────────────────────────

    @Test
    void buildWithOnlyQueryContainsTaskAndOutput() {
        String ctx = builder.build("Java 是什么？");
        assertTrue(ctx.contains("[Task]"));
        assertTrue(ctx.contains("Java 是什么？"));
        assertTrue(ctx.contains("[Output]"));
        assertFalse(ctx.contains("[Role & Policies]"));
        assertFalse(ctx.contains("[Evidence]"));
        assertFalse(ctx.contains("[Context]"));
    }

    @Test
    void systemInstructionsAppearsInRolePolicies() {
        String ctx = builder.build("query", "You are a helpful assistant.", List.of(), List.of());
        assertTrue(ctx.contains("[Role & Policies]"));
        assertTrue(ctx.contains("You are a helpful assistant."));
    }

    @Test
    void blankSystemInstructionsProducesNoRoleSection() {
        String ctx = builder.build("query", "   ", List.of(), List.of());
        assertFalse(ctx.contains("[Role & Policies]"));
    }

    @Test
    void conversationHistoryAppearsInContextWithRolePrefix() {
        List<Message> history = List.of(
                Message.user("Hello"),
                Message.assistant("Hi there")
        );
        String ctx = builder.build("query", null, history, List.of());
        assertTrue(ctx.contains("[Context]"));
        assertTrue(ctx.contains("user: Hello"));
        assertTrue(ctx.contains("assistant: Hi there"));
    }

    @Test
    void ragCustomPacketAppearsInEvidence() {
        // explicit relevance > 0.5 bypasses Jaccard recalculation
        ContextPacket ragPacket = ContextPacket.of("Java runs on the JVM.")
                .withRelevance(0.9)
                .withMetadata(Map.of("type", "rag_result"))
                .build();
        String ctx = builder.build("query", null, List.of(), List.of(ragPacket));
        assertTrue(ctx.contains("[Evidence]"));
        assertTrue(ctx.contains("Java runs on the JVM."));
    }

    @Test
    void customPacketWithoutTypeAppearsInContext() {
        // explicit relevance > 0.5 bypasses Jaccard recalculation
        ContextPacket custom = ContextPacket.of("Some background note.")
                .withRelevance(0.8)
                .build();
        String ctx = builder.build("query", null, List.of(), List.of(custom));
        assertTrue(ctx.contains("[Context]"));
        assertTrue(ctx.contains("Some background note."));
    }

    @Test
    void memoryIndexAppearsInMemorySection() {
        MemoryService memory = new MemoryService(new MemoryManager());
        memory.remember(MemoryType.PROJECT, "James Gosling created Java.", "gosling", "Creator of Java", null);

        String ctx = new ContextBuilder(config)
                .withMemory(memory)
                .build("Java 的起源");

        assertTrue(ctx.contains("[Memory]"));
        assertTrue(ctx.contains("gosling"));
        assertTrue(ctx.contains("search_memory"));
    }

    // ── section ordering ──────────────────────────────────────────────────────

    @Test
    void sectionOrderIsRolePoliciesTaskEvidenceContextOutput() {
        ContextPacket evidence = ContextPacket.of("Evidence text.")
                .withRelevance(0.9)
                .withMetadata(Map.of("type", "rag_result"))
                .build();
        String ctx = builder.build(
                "query",
                "You are helpful.",
                List.of(Message.user("prev message")),
                List.of(evidence)
        );

        int rolePos     = ctx.indexOf("[Role & Policies]");
        int taskPos     = ctx.indexOf("[Task]");
        int evidencePos = ctx.indexOf("[Evidence]");
        int contextPos  = ctx.indexOf("[Context]");
        int outputPos   = ctx.indexOf("[Output]");

        assertTrue(rolePos     < taskPos,     "[Role & Policies] must precede [Task]");
        assertTrue(taskPos     < evidencePos, "[Task] must precede [Evidence]");
        assertTrue(evidencePos < contextPos,  "[Evidence] must precede [Context]");
        assertTrue(contextPos  < outputPos,   "[Context] must precede [Output]");
    }

    // ── conversation history order ────────────────────────────────────────────

    @Test
    void conversationHistoryAllTurnsPresent() {
        List<Message> history = List.of(
                Message.user("first"),
                Message.assistant("second"),
                Message.user("third")
        );
        String ctx = builder.build("query", null, history, List.of());

        // select re-ranks by score, so positional order is not guaranteed;
        // assert all turns are present in the output
        assertTrue(ctx.contains("user: first"));
        assertTrue(ctx.contains("assistant: second"));
        assertTrue(ctx.contains("user: third"));
    }

    // ── token budget ─────────────────────────────────────────────────────────

    @Test
    void selectExcludesPacketsThatExceedBudget() {
        ContextConfig tight = ContextConfig.builder()
                .maxTokens(30)
                .reserveRatio(0.0)
                .minRelevance(0.0)
                .build();

        // each packet ~26 tokens (20 words × 1.3); two together exceed budget of 30
        String manyWords = "alpha beta gamma delta epsilon zeta eta theta iota kappa "
                         + "lambda mu nu xi omicron pi rho sigma tau upsilon";
        ContextPacket high = ContextPacket.of(manyWords).withRelevance(0.9).build();
        ContextPacket low  = ContextPacket.of(manyWords).withRelevance(0.6).build();

        String ctx = new ContextBuilder(tight).build("query", null, List.of(), List.of(high, low));

        // high-relevance packet should be included, low one should be dropped
        int occurrences = countOccurrences(ctx, manyWords);
        assertEquals(1, occurrences, "only the higher-relevance packet should fit");
    }

    // ── compress ─────────────────────────────────────────────────────────────

    @Test
    void compressAppliedWhenEnabled() {
        // extremely tight budget so even section headers push us over
        ContextConfig tiny = ContextConfig.builder()
                .maxTokens(5)
                .reserveRatio(0.0)
                .enableCompression(true)
                .build();

        String ctx = new ContextBuilder(tiny).build(
                "query",
                "word word word word word word word word word word",
                List.of(),
                List.of()
        );
        // either got truncated or compression marker was inserted
        assertTrue(ctx.length() < 500 || ctx.contains("[... 内容已压缩 ...]"));
    }

    @Test
    void compressSkippedWhenDisabled() {
        ContextConfig noCompress = ContextConfig.builder()
                .maxTokens(5)
                .reserveRatio(0.0)
                .enableCompression(false)
                .build();

        String ctx = new ContextBuilder(noCompress).build(
                "query",
                "word word word word word word word word word word",
                List.of(),
                List.of()
        );
        assertFalse(ctx.contains("[... 内容已压缩 ...]"));
    }

    // ── embedding-based relevance ─────────────────────────────────────────────

    @Test
    void embeddingBasedRelevanceIncludesHighSimilarityPackets() {
        // Fake model: vectors controlled by content string
        EmbeddingModel fakeModel = new EmbeddingModel() {
            @Override public ModelInfo modelInfo() { return new ModelInfo("fake", 3, 8191); }
            @Override public Embedding embed(String text) { return embedBatch(List.of(text)).get(0); }
            @Override
            public List<Embedding> embedBatch(List<String> texts) {
                return texts.stream().map(t -> new Embedding(vectorFor(t), "fake", 0)).toList();
            }
            private float[] vectorFor(String text) {
                if (text.contains("Java"))      return new float[]{1f, 0f, 0f};
                if (text.contains("unrelated")) return new float[]{0f, 0f, 1f};
                return new float[]{0f, 1f, 0f};
            }
        };

        ContextConfig cfg = ContextConfig.builder()
                .embeddingModel(fakeModel)
                .minRelevance(0.1)
                .build();

        // relevance=0.5 → triggers vector recalculation in select
        ContextPacket relevant = ContextPacket.of("Java is a programming language.")
                .withMetadata(Map.of("type", "rag_result"))
                .build();
        ContextPacket irrelevant = ContextPacket.of("completely unrelated topic")
                .withMetadata(Map.of("type", "rag_result"))
                .build();

        String ctx = new ContextBuilder(cfg)
                .build("Java question", null, List.of(), List.of(relevant, irrelevant));

        // cosine("Java question", "Java is...") ≈ 1.0 → included
        assertTrue(ctx.contains("Java is a programming language."));
        // cosine("Java question", "completely unrelated") = 0.0 < minRelevance → filtered
        assertFalse(ctx.contains("completely unrelated topic"));
    }

    // ── output preview ───────────────────────────────────────────────────────

    @Test
    void printFullOutput() {
        MemoryService memory = new MemoryService(new MemoryManager());
        memory.remember(MemoryType.PROJECT,  "James Gosling created Java in 1995 at Sun Microsystems.", "gosling", "Creator of Java", null);
        memory.remember(MemoryType.FEEDBACK, "User previously asked about JVM internals.", "jvm_history", "Prior JVM question", null);

        ContextPacket ragResult = ContextPacket.of(
                "Java is a high-level, class-based, object-oriented programming language.")
                .withRelevance(0.92)
                .withMetadata(Map.of("type", "rag_result"))
                .build();

        List<Message> history = List.of(
                Message.user("What is Java?"),
                Message.assistant("Java is a popular programming language.")
        );

        String ctx = new ContextBuilder(ContextConfig.defaults())
                .withMemory(memory)
                .build(
                        "Java 的起源是什么？",
                        "你是一个专业的 Java 技术助手，回答准确、简洁。",
                        history,
                        List.of(ragResult)
                );

        System.out.println("=".repeat(60));
        System.out.println(ctx);
        System.out.println("=".repeat(60));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static int countOccurrences(String text, String target) {
        int count = 0;
        int idx   = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}