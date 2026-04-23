package com.helloagents.rag.core;

import java.util.Arrays;

/**
 * 单次嵌入调用的结果。
 *
 * @param vector     嵌入向量
 * @param modelId    生成此向量所用的模型 ID
 * @param tokenCount 本次调用消耗的 token 数（API 返回值；-1 表示不可用）
 */
public record Embedding(float[] vector, String modelId, int tokenCount) {

    public int dimension() {
        return vector.length;
    }

    // float[] 不具备值相等语义，显式覆盖
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Embedding other)) return false;
        return tokenCount == other.tokenCount
                && modelId.equals(other.modelId)
                && Arrays.equals(vector, other.vector);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(vector) + modelId.hashCode()) + tokenCount;
    }

    @Override
    public String toString() {
        return "Embedding[model=" + modelId + ", dim=" + vector.length + ", tokens=" + tokenCount + "]";
    }
}