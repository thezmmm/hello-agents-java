package com.helloagents.rag.embedding;

import com.helloagents.rag.core.Embedding;
import com.helloagents.rag.core.EmbeddingModel;
import com.helloagents.rag.core.ModelInfo;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;

import io.github.cdimascio.dotenv.Dotenv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAiEmbeddingModel implements EmbeddingModel {

    /** 已知模型的静态属性，dimension / maxInputTokens */
    private static final Map<String, ModelInfo> KNOWN_MODELS = Map.of(
            "text-embedding-3-small", new ModelInfo("text-embedding-3-small", 1536, 8191),
            "text-embedding-3-large", new ModelInfo("text-embedding-3-large", 3072, 8191),
            "text-embedding-ada-002", new ModelInfo("text-embedding-ada-002", 1536, 8191)
    );

    private final OpenAIClient client;
    private final ModelInfo modelInfo;

    public OpenAiEmbeddingModel(String apiKey, String baseUrl, String modelId) {
        this.client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        this.modelInfo = KNOWN_MODELS.getOrDefault(modelId,
                new ModelInfo(modelId, 1536, 8191)); // 未知模型给默认值
    }

    public OpenAiEmbeddingModel(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, "text-embedding-3-small");
    }

    public static OpenAiEmbeddingModel fromEnv() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String apiKey = dotenv.get("LLM_API_KEY");
        if (apiKey == null || apiKey.isBlank()) apiKey = dotenv.get("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Missing API key: set LLM_API_KEY or OPENAI_API_KEY in .env or environment");
        }
        String baseUrl = dotenv.get("LLM_BASE_URL", "https://api.openai.com/v1");
        String model   = dotenv.get("EMBEDDING_MODEL", "text-embedding-3-small");
        return new OpenAiEmbeddingModel(apiKey, baseUrl, model);
    }

    @Override
    public ModelInfo modelInfo() {
        return modelInfo;
    }

    @Override
    public Embedding embed(String text) {
        try {
            CreateEmbeddingResponse response = client.embeddings().create(
                    EmbeddingCreateParams.builder()
                            .model(modelInfo.modelId())
                            .input(EmbeddingCreateParams.Input.ofString(text))
                            .build());
            var data = response.data().get(0);
            int tokens = response.usage().promptTokens() > 0
                    ? (int) response.usage().promptTokens() : -1;
            return new Embedding(toFloatArray(data.embedding()), modelInfo.modelId(), tokens);
        } catch (Exception e) {
            throw toEmbeddingException(e, "embed");
        }
    }

    /**
     * 批量嵌入，返回顺序与输入严格一一对应。
     * openai-java SDK 支持批量输入，响应按 index 字段排序后与输入对齐。
     */
    @Override
    public List<Embedding> embedBatch(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        try {
            CreateEmbeddingResponse response = client.embeddings().create(
                    EmbeddingCreateParams.builder()
                            .model(modelInfo.modelId())
                            .input(EmbeddingCreateParams.Input.ofArrayOfStrings(texts))
                            .build());

            int totalTokens = (int) response.usage().promptTokens();
            int perItem = texts.isEmpty() ? -1 : totalTokens / texts.size();

            // 按 index 排序保证与输入一一对应
            List<Embedding> result = new ArrayList<>(texts.size());
            response.data().stream()
                    .sorted(java.util.Comparator.comparingInt(e -> (int) e.index()))
                    .forEach(e -> result.add(new Embedding(toFloatArray(e.embedding()),
                            modelInfo.modelId(), perItem)));
            return result;
        } catch (Exception e) {
            throw toEmbeddingException(e, "embedBatch");
        }
    }

    private EmbeddingException toEmbeddingException(Exception e, String op) {
        if (e instanceof EmbeddingException ee) return ee;
        String msg = op + " failed: " + e.getMessage();
        if (e instanceof OpenAIServiceException svc) {
            int status = svc.statusCode();
            if (status == 429) return new EmbeddingException(EmbeddingException.ErrorKind.RATE_LIMITED, msg, e);
            if (status == 401 || status == 403) return new EmbeddingException(EmbeddingException.ErrorKind.AUTHENTICATION_ERROR, msg, e);
            if (status == 400 && msg.contains("token")) return new EmbeddingException(EmbeddingException.ErrorKind.TOKEN_LIMIT_EXCEEDED, msg, e);
        }
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException) {
            return new EmbeddingException(EmbeddingException.ErrorKind.NETWORK_ERROR, msg, e);
        }
        return new EmbeddingException(EmbeddingException.ErrorKind.UNKNOWN, msg, e);
    }

    private static float[] toFloatArray(List<Double> doubles) {
        float[] arr = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) arr[i] = doubles.get(i).floatValue();
        return arr;
    }
}