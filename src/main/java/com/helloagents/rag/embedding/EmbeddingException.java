package com.helloagents.rag.embedding;

/**
 * 嵌入调用失败时抛出的异常。
 * 通过 {@link ErrorKind} 区分失败原因，便于上层按策略处理（重试、降级、报错）。
 */
public class EmbeddingException extends RuntimeException {

    public enum ErrorKind {
        /** 网络连接失败、超时 */
        NETWORK_ERROR,
        /** API 限流（HTTP 429） */
        RATE_LIMITED,
        /** 输入文本超过模型 maxInputTokens */
        TOKEN_LIMIT_EXCEEDED,
        /** API Key 无效或权限不足 */
        AUTHENTICATION_ERROR,
        /** 其他未归类错误 */
        UNKNOWN
    }

    private final ErrorKind kind;

    public EmbeddingException(ErrorKind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public EmbeddingException(ErrorKind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public ErrorKind kind() {
        return kind;
    }

    @Override
    public String toString() {
        return "EmbeddingException[" + kind + "] " + getMessage();
    }
}