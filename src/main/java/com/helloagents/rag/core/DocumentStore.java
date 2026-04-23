package com.helloagents.rag.core;

import java.util.List;
import java.util.Optional;

public interface DocumentStore {

    void save(Document document);

    Optional<Document> get(String id);

    boolean delete(String id);

    List<Document> listAll();

    int size();
}