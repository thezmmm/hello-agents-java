package com.helloagents.rag.core;

import com.helloagents.rag.embedding.EmbeddingException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EmbeddingModel {

    /**
     * 返回该模型的静态属性（ID、维度、最大输入 token 数）。
     * 无需实例化多次查询——调用方可在初始化时缓存此值，用于分块策略决策。
     */
    ModelInfo modelInfo();

    /**
     * 将单段文本转为嵌入向量。
     *
     * @param text 输入文本，长度不得超过 {@code modelInfo().maxInputTokens()}
     * @return 嵌入结果，包含向量、模型 ID、token 消耗
     * @throws EmbeddingException 网络错误、限流、超长、认证失败时抛出，通过 {@link EmbeddingException#kind()} 区分
     */
    Embedding embed(String text);

    /**
     * 批量嵌入。
     *
     * <p><b>顺序保证：</b>返回列表与输入列表严格一一对应，
     * 即 {@code result.get(i)} 对应 {@code texts.get(i)}，实现方必须保证此语义。
     *
     * @param texts 输入文本列表，每项长度不得超过 {@code modelInfo().maxInputTokens()}
     * @return 与输入等长、索引一一对应的嵌入列表
     * @throws EmbeddingException 任一文本调用失败时抛出
     */
    List<Embedding> embedBatch(List<String> texts);

    /**
     * 异步批量嵌入，默认实现包装同步调用。
     * IO 密集型场景下，实现方可覆盖此方法以使用真正的异步 HTTP 客户端。
     *
     * <p>顺序保证与 {@link #embedBatch} 相同。
     */
    default CompletableFuture<List<Embedding>> embedBatchAsync(List<String> texts) {
        return CompletableFuture.supplyAsync(() -> embedBatch(texts));
    }

    /** 向量维度（快捷方法，等价于 {@code modelInfo().dimension()}）。 */
    default int dimension() {
        return modelInfo().dimension();
    }

    /** 单次最大输入 token 数（快捷方法，等价于 {@code modelInfo().maxInputTokens()}）。 */
    default int maxInputTokens() {
        return modelInfo().maxInputTokens();
    }
}