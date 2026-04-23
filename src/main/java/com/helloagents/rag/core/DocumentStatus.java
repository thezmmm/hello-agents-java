package com.helloagents.rag.core;

public enum DocumentStatus {
    /** 已入库，等待分块与向量化 */
    PENDING,
    /** 分块、向量化完成，可被检索 */
    INDEXED,
    /** 索引过程中发生错误 */
    FAILED,
    /** 已软删除，不参与检索 */
    DELETED
}