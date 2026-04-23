package com.helloagents.rag.core;

/**
 * 嵌入模型的静态属性，通过 {@link EmbeddingModel#modelInfo()} 暴露。
 *
 * @param modelId        模型标识符，如 "text-embedding-3-small"
 * @param dimension      向量维度
 * @param maxInputTokens 单次调用允许的最大输入 token 数
 */
public record ModelInfo(String modelId, int dimension, int maxInputTokens) {}