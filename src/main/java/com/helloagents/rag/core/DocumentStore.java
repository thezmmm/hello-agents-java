package com.helloagents.rag.core;

import java.util.List;
import java.util.Optional;

public interface DocumentStore {

    void save(Document document);

    Optional<Document> get(String id);

    /**
     * 更新文档状态，不修改其他字段。
     * @return 文档不存在时返回 false
     */
    boolean updateStatus(String id, DocumentStatus status);

    /** 硬删除（从存储中完全移除）。软删除请用 updateStatus(id, DELETED)。 */
    boolean delete(String id);

    /** 返回所有非 DELETED 文档 */
    List<Document> listAll();

    /** 按状态过滤 */
    List<Document> listByStatus(DocumentStatus status);

    int size();
}