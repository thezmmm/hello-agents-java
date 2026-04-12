package com.helloagents.core;

import com.helloagents.llm.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base implementation of {@link BaseAgent} that provides conversation history management.
 */
public abstract class AbstractAgent implements BaseAgent {

    private final List<Message> history = new ArrayList<>();

    @Override
    public void addMessage(Message message) {
        history.add(message);
    }

    @Override
    public List<Message> getHistory() {
        return Collections.unmodifiableList(history);
    }

    @Override
    public void clearHistory() {
        history.clear();
    }
}
