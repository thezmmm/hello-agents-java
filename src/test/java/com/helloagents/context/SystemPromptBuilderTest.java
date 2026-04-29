package com.helloagents.context;

import com.helloagents.memory.MemoryManager;
import com.helloagents.memory.MemoryService;
import com.helloagents.memory.core.MemoryType;
import com.helloagents.rag.app.RagSystem;
import com.helloagents.rag.core.Embedding;
import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.core.KnowledgeBase;
import com.helloagents.rag.core.ModelInfo;
import com.helloagents.rag.store.InMemoryDocumentStore;
import com.helloagents.rag.store.InMemoryVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptBuilderTest {

    private ContextConfig        config;
    private SystemPromptBuilder  builder;

    @BeforeEach
    void setUp() {
        config  = ContextConfig.defaults();
        builder = new SystemPromptBuilder(config);
    }

    // ── section presence ──────────────────────────────────────────────────────

    @Test
    void buildWithOnlyQueryContainsOutput() {
        String ctx = builder.build("What is Java?");
        assertTrue(ctx.contains("[Output]"));
        assertFalse(ctx.contains("[Task]"));
        assertFalse(ctx.contains("[Role & Policies]"));
        assertFalse(ctx.contains("[Evidence]"));
        assertFalse(ctx.contains("[Context]"));
    }

    @Test
    void systemInstructionsAppearsInRolePolicies() {
        String ctx = builder.build("query", "You are a helpful assistant.");
        assertTrue(ctx.contains("[Role & Policies]"));
        assertTrue(ctx.contains("You are a helpful assistant."));
    }

    @Test
    void blankSystemInstructionsProducesNoRoleSection() {
        String ctx = builder.build("query", "   ");
        assertFalse(ctx.contains("[Role & Policies]"));
    }

    @Test
    void ragResultAppearsInEvidence() {
        RagSystem rag = uniformRag("Java runs on the JVM.");
        String ctx = new SystemPromptBuilder(config)
                .withRag(rag)
                .build("Java question");
        assertTrue(ctx.contains("[Evidence]"));
        assertTrue(ctx.contains("Java runs on the JVM."));
    }

    @Test
    void memoryIndexAppearsInMemorySection() {
        MemoryService memory = new MemoryService(new MemoryManager());
        memory.remember(MemoryType.PROJECT, "James Gosling created Java.", "gosling", "Creator of Java", null);

        String ctx = new SystemPromptBuilder(config)
                .withMemory(memory)
                .build("Java 的起源");

        assertTrue(ctx.contains("[Memory]"));
        assertTrue(ctx.contains("gosling"));
        assertTrue(ctx.contains("search_memory"));
    }

    // ── [Task] and [Context] sections must never appear ───────────────────────

    @Test
    void taskAndContextSectionsNeverAppear() {
        MemoryService memory = new MemoryService(new MemoryManager());
        memory.remember(MemoryType.PROJECT, "some note", "key", "desc", null);
        RagSystem rag = uniformRag("some document");

        String ctx = new SystemPromptBuilder(config)
                .withMemory(memory)
                .withRag(rag)
                .build("query", "You are helpful.");

        assertFalse(ctx.contains("[Task]"));
        assertFalse(ctx.contains("[Context]"));
    }

    // ── section ordering ──────────────────────────────────────────────────────

    @Test
    void sectionOrderIsRolePoliciesMemoryEvidenceOutput() {
        MemoryService memory = new MemoryService(new MemoryManager());
        memory.remember(MemoryType.PROJECT, "note", "key", "desc", null);
        RagSystem rag = uniformRag("evidence text");

        ContextConfig cfg = ContextConfig.builder().minRelevance(0.0).build();
        String ctx = new SystemPromptBuilder(cfg)
                .withMemory(memory)
                .withRag(rag)
                .build("query", "You are helpful.");

        int rolePos     = ctx.indexOf("[Role & Policies]");
        int memoryPos   = ctx.indexOf("[Memory]");
        int evidencePos = ctx.indexOf("[Evidence]");
        int outputPos   = ctx.indexOf("[Output]");

        assertTrue(rolePos     < memoryPos,   "[Role & Policies] must precede [Memory]");
        assertTrue(memoryPos   < evidencePos, "[Memory] must precede [Evidence]");
        assertTrue(evidencePos < outputPos,   "[Evidence] must precede [Output]");
    }

    // ── embedding-based relevance ─────────────────────────────────────────────

    @Test
    void embeddingBasedRelevanceFiltersIrrelevantResults() {
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

        KnowledgeBase kb = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
        RagSystem rag = new RagSystem(fakeModel, kb);
        rag.addDocument("doc1", "Java is a programming language.");
        rag.addDocument("doc2", "completely unrelated topic here");

        ContextConfig cfg = ContextConfig.builder()
                .embeddingModel(fakeModel)
                .minRelevance(0.1)
                .build();

        String ctx = new SystemPromptBuilder(cfg).withRag(rag).build("Java question");

        assertTrue(ctx.contains("Java is a programming language."),  "relevant result should be included");
        assertFalse(ctx.contains("completely unrelated topic here"), "irrelevant result should be filtered");
    }

    // ── compress ─────────────────────────────────────────────────────────────

    @Test
    void compressAppliedWhenEnabled() {
        ContextConfig tiny = ContextConfig.builder()
                .maxTokens(5)
                .reserveRatio(0.0)
                .enableCompression(true)
                .build();

        String ctx = new SystemPromptBuilder(tiny).build(
                "query",
                "word word word word word word word word word word"
        );
        assertTrue(ctx.length() < 500 || ctx.contains("[... 内容已压缩 ...]"));
    }

    @Test
    void compressSkippedWhenDisabled() {
        ContextConfig noCompress = ContextConfig.builder()
                .maxTokens(5)
                .reserveRatio(0.0)
                .enableCompression(false)
                .build();

        String ctx = new SystemPromptBuilder(noCompress).build(
                "query",
                "word word word word word word word word word word"
        );
        assertFalse(ctx.contains("[... 内容已压缩 ...]"));
    }

    // ── output preview ────────────────────────────────────────────────────────

    @Test
    void printFullOutput() {
        MemoryService memory = new MemoryService(new MemoryManager());
        memory.remember(MemoryType.PROJECT,  "James Gosling created Java in 1995 at Sun Microsystems.", "gosling", "Creator of Java", null);
        memory.remember(MemoryType.FEEDBACK, "User previously asked about JVM internals.", "jvm_history", "Prior JVM question", null);

        RagSystem rag = uniformRag("Java is a high-level, class-based, object-oriented programming language.");

        String ctx = new SystemPromptBuilder(ContextConfig.defaults())
                .withMemory(memory)
                .withRag(rag)
                .build("What is the origin of Java?",
                       "You are a professional Java assistant. Be accurate and concise.");

        System.out.println("=".repeat(60));
        System.out.println(ctx);
        System.out.println("=".repeat(60));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** RagSystem where all embeddings are identical — uniform relevance for all docs. */
    private static RagSystem uniformRag(String... docs) {
        EmbeddingModel uniform = new EmbeddingModel() {
            @Override public ModelInfo modelInfo() { return new ModelInfo("uniform", 1, 8191); }
            @Override public Embedding embed(String text) { return embedBatch(List.of(text)).get(0); }
            @Override public List<Embedding> embedBatch(List<String> texts) {
                return texts.stream()
                        .map(t -> new Embedding(new float[]{1f}, "uniform", 0))
                        .toList();
            }
        };
        KnowledgeBase kb = new KnowledgeBase(new InMemoryDocumentStore(), new InMemoryVectorStore());
        RagSystem rag = new RagSystem(uniform, kb);
        for (int i = 0; i < docs.length; i++) {
            rag.addDocument("doc" + i, docs[i]);
        }
        return rag;
    }
}