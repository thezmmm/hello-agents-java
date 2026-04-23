package com.helloagents.rag.store;

import com.helloagents.rag.core.Document;
import com.helloagents.rag.core.DocumentStatus;
import com.helloagents.rag.core.DocumentStore;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryDocumentStore implements DocumentStore {

    private final Map<String, Document> store = new LinkedHashMap<>();

    @Override
    public void save(Document document) {
        store.put(document.id(), document);
    }

    @Override
    public Optional<Document> get(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean updateStatus(String id, DocumentStatus status) {
        Document doc = store.get(id);
        if (doc == null) return false;
        store.put(id, doc.withStatus(status));
        return true;
    }

    @Override
    public boolean delete(String id) {
        return store.remove(id) != null;
    }

    /** 返回所有非 DELETED 文档 */
    @Override
    public List<Document> listAll() {
        return store.values().stream()
                .filter(d -> d.status() != DocumentStatus.DELETED)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<Document> listByStatus(DocumentStatus status) {
        return store.values().stream()
                .filter(d -> d.status() == status)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public int size() {
        return (int) store.values().stream()
                .filter(d -> d.status() != DocumentStatus.DELETED)
                .count();
    }
}