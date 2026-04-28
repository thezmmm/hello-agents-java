package com.helloagents.rag.embedding;

import com.helloagents.rag.core.Embedding;
import com.helloagents.rag.core.ModelInfo;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OpenAiEmbeddingModelTest {

    /** 优先读系统环境变量，不存在时尝试 .env 文件 */
    private static final String API_KEY = resolveApiKey();

    private static String resolveApiKey() {
        String key = System.getenv("LLM_API_KEY");
        if (key != null && !key.isBlank()) return key;
        try {
            return Dotenv.load().get("LLM_API_KEY", null);
        } catch (DotenvException e) {
            return null;
        }
    }

    private static final String BASE_URL = resolveBaseUrl();

    private static String resolveBaseUrl() {
        String url = System.getenv("LLM_BASE_URL");
        if (url != null && !url.isBlank()) return url;
        try {
            String fromEnv = Dotenv.load().get("LLM_BASE_URL", null);
            if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        } catch (DotenvException ignored) {}
        return "https://api.openai.com/v1";
    }

    /** 集成测试前检查，没有 Key 则跳过，不报错 */
    @BeforeEach
    void requireApiKey(org.junit.jupiter.api.TestInfo info) {
        if (info.getTags().contains("integration")) {
            assumeTrue(API_KEY != null && !API_KEY.isBlank(),
                    "跳过集成测试：未配置 LLM_API_KEY（系统环境变量或 .env 文件）");
        }
    }

    // ── 单元测试（无需网络/API Key）───────────────────────────────────────

    @Test
    void modelInfo_knownSmallModel() {
        var model = new OpenAiEmbeddingModel("dummy", BASE_URL, "text-embedding-3-small");
        ModelInfo info = model.modelInfo();
        assertEquals("text-embedding-3-small", info.modelId());
        assertEquals(1536, info.dimension());
        assertEquals(8191, info.maxInputTokens());
    }

    @Test
    void modelInfo_knownLargeModel() {
        var model = new OpenAiEmbeddingModel("dummy", BASE_URL, "text-embedding-3-large");
        assertEquals(3072, model.modelInfo().dimension());
    }

    @Test
    void modelInfo_unknownModel_usesDefaults() {
        var model = new OpenAiEmbeddingModel("dummy", BASE_URL, "my-custom-model");
        ModelInfo info = model.modelInfo();
        assertEquals("my-custom-model", info.modelId());
        assertEquals(1536, info.dimension());
        assertEquals(8191, info.maxInputTokens());
    }

    @Test
    void defaultConstructor_usesSmallModel() {
        var model = new OpenAiEmbeddingModel("dummy", BASE_URL);
        assertEquals("text-embedding-3-small", model.modelInfo().modelId());
    }

    @Test
    void defaultMethods_delegateToModelInfo() {
        var model = new OpenAiEmbeddingModel("dummy", BASE_URL, "text-embedding-3-small");
        assertEquals(model.modelInfo().dimension(), model.dimension());
        assertEquals(model.modelInfo().maxInputTokens(), model.maxInputTokens());
    }

    @Test
    void embedBatch_emptyInput_returnsEmptyList() {
        var model = new OpenAiEmbeddingModel("dummy", BASE_URL);
        assertTrue(model.embedBatch(List.of()).isEmpty());
    }

    // ── 集成测试（需要 LLM_API_KEY）──────────────────────────────────────

    @Test
    @org.junit.jupiter.api.Tag("integration")
    void embed_dimensionMatchesModelInfo() {
        var model = new OpenAiEmbeddingModel(API_KEY, BASE_URL);
        Embedding result = model.embed("Hello, world!");
        assertEquals(model.dimension(), result.vector().length);
        assertEquals(model.modelInfo().modelId(), result.modelId());
        assertTrue(result.tokenCount() > 0);
    }

    @Test
    @org.junit.jupiter.api.Tag("integration")
    void embedBatch_countMatchesInput() {
        var model = new OpenAiEmbeddingModel(API_KEY, BASE_URL);
        var texts = List.of("Java", "Python", "Go");
        assertEquals(texts.size(), model.embedBatch(texts).size());
    }

    @Test
    @org.junit.jupiter.api.Tag("integration")
    void embedBatch_allDimensionsConsistent() {
        var model = new OpenAiEmbeddingModel(API_KEY, BASE_URL);
        var results = model.embedBatch(List.of("short", "a much longer sentence with many more words"));
        results.forEach(e -> assertEquals(model.dimension(), e.vector().length));
    }

    @Test
    @org.junit.jupiter.api.Tag("integration")
    void embedBatch_orderPreserved() {
        var model = new OpenAiEmbeddingModel(API_KEY, BASE_URL);
        var batch = model.embedBatch(List.of("apple", "banana", "cherry"));
        var single = model.embed("apple");
        double sim = cosineSimilarity(single.vector(), batch.get(0).vector());
        assertTrue(sim > 0.99,
                "batch[0] 应与单独 embed 同文本高度相似，实际余弦相似度: " + sim);
    }

    @Test
    @org.junit.jupiter.api.Tag("integration")
    void embed_semanticSimilarity() {
        var model = new OpenAiEmbeddingModel(API_KEY, BASE_URL);
        var cat    = model.embed("cat");
        var kitten = model.embed("kitten");
        var plane  = model.embed("airplane");
        double catKitten = cosineSimilarity(cat.vector(), kitten.vector());
        double catPlane  = cosineSimilarity(cat.vector(), plane.vector());
        assertTrue(catKitten > catPlane,
                "cat↔kitten (%.4f) 应高于 cat↔airplane (%.4f)".formatted(catKitten, catPlane));
    }

    @Test
    @org.junit.jupiter.api.Tag("integration")
    void embed_invalidKey_throwsAuthenticationError() {
        var model = new OpenAiEmbeddingModel("invalid-key-000", BASE_URL);
        EmbeddingException ex = assertThrows(EmbeddingException.class, () -> model.embed("test"));
        assertEquals(EmbeddingException.ErrorKind.AUTHENTICATION_ERROR, ex.kind());
    }

    // ── helper ────────────────────────────────────────────────────────────

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}